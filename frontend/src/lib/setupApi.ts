import api from './api';
import type { OfficialRole } from './adminApi';

// ── Types ──────────────────────────────────────────────────────────────────

export type SetupStatusDto = { bootstrapped: boolean; setupComplete: boolean };

export type SetupProgressDto = {
  club: boolean;
  track: boolean;
  format: boolean;
  staff: boolean;
  decoder: boolean;
};

export type BootstrapRequest = {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
};

export type AuthResponse = {
  accessToken: string;
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: OfficialRole[];
};

export type DecoderConfigUpdateRequest = {
  decoderHost: string;
  decoderPort: number;
  decoderProtocol: 'RC4' | 'P3';
};

/** Stored decoder settings. Fields are null until the decoder has been configured. */
export type DecoderConfigDto = {
  decoderHost: string | null;
  decoderPort: number | null;
  decoderProtocol: 'RC4' | 'P3' | null;
};

export type SetupStaffRequest = {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  roles: OfficialRole[];
};

// ── API functions ──────────────────────────────────────────────────────────

export async function getSetupStatus(): Promise<SetupStatusDto> {
  const { data } = await api.get<SetupStatusDto>('/api/v1/setup/status');
  return data;
}

export async function getSetupProgress(): Promise<SetupProgressDto> {
  const { data } = await api.get<SetupProgressDto>('/api/v1/setup/progress');
  return data;
}

export async function bootstrap(req: BootstrapRequest): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/api/v1/setup/bootstrap', req);
  return data;
}

export async function getDecoderConfig(): Promise<DecoderConfigDto> {
  const { data } = await api.get<DecoderConfigDto>('/api/v1/setup/decoder-config');
  return data;
}

export type DecoderTestResult = { ok: boolean; message: string };

/** Tests a decoder address without saving it or changing the live listener. */
export async function testDecoderConfig(req: DecoderConfigUpdateRequest): Promise<DecoderTestResult> {
  // The server waits up to 5 s to connect and 8 s for a record. Allow a little more than that.
  const { data } = await api.post<DecoderTestResult>('/api/v1/setup/decoder-config/test', req, {
    timeout: 15_000,
  });
  return data;
}

export async function updateDecoderConfig(req: DecoderConfigUpdateRequest): Promise<SetupProgressDto> {
  const { data } = await api.patch<SetupProgressDto>('/api/v1/setup/decoder-config', req);
  return data;
}

export async function createSetupStaff(req: SetupStaffRequest): Promise<void> {
  await api.post('/api/v1/setup/staff', req);
}


/** The laptop's network name settings, answer of {@code GET /api/v1/setup/network-name}. */
export type NetworkNameDto = {
  /** The name in the settings file, such as "rctiming"; empty when none is set. */
  configuredName: string;
  /** The name being announced now, such as "rctiming.local"; null until a network interface has taken it. */
  announcedHost: string | null;
  suggestedName: string;
  /** The saved name differs from the one this run started with. */
  restartNeeded: boolean;
};

export async function getNetworkName(): Promise<NetworkNameDto> {
  const { data } = await api.get<NetworkNameDto>('/api/v1/setup/network-name');
  return data;
}

export async function saveNetworkName(name: string): Promise<NetworkNameDto> {
  const { data } = await api.put<NetworkNameDto>('/api/v1/setup/network-name', { name });
  return data;
}
