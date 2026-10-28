import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import type { LiveTimingSettingsDto, UpdateClubProfileRequest } from '@/lib/adminApi';
import { publicQueryKeys } from '../publicQueryKeys';
import { adminQueryKeys } from './adminQueryKeys';

export function useClubProfile() {
  return useQuery({
    queryKey: adminQueryKeys.club.profile(),
    queryFn: adminApi.club.getProfile,
  });
}

export function useUpdateClubProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateClubProfileRequest) => adminApi.club.updateProfile(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.club.profile() }),
  });
}

export function useUploadClubLogo() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => adminApi.club.uploadLogo(file),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.club.profile() }),
  });
}

export function usePrivacyNotice() {
  return useQuery({
    queryKey: adminQueryKeys.club.privacyNotice(),
    queryFn: adminApi.club.getPrivacyNotice,
  });
}

/** Saves the club's own notice; a blank text goes back to the default. The public page caches it, so that is refreshed too. */
export function useSetPrivacyNotice() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (text: string) => adminApi.club.setPrivacyNotice(text),
    onSuccess: (saved) => {
      qc.setQueryData(adminQueryKeys.club.privacyNotice(), saved);
      void qc.invalidateQueries({ queryKey: publicQueryKeys.privacyNotice() });
    },
  });
}

export function useLiveTimingSettings() {
  return useQuery({
    queryKey: adminQueryKeys.club.liveTimingSettings(),
    queryFn: adminApi.club.getLiveTimingSettings,
  });
}

/** Saves the live timing thresholds (#505); races in progress take them from their next update. */
export function useSetLiveTimingSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (settings: LiveTimingSettingsDto) => adminApi.club.setLiveTimingSettings(settings),
    onSuccess: (saved) => {
      qc.setQueryData(adminQueryKeys.club.liveTimingSettings(), saved);
      // The boards draw with the close gap and the late margin
      void qc.invalidateQueries({ queryKey: publicQueryKeys.boards.settings() });
    },
  });
}

/** The HTTPS certificates' state (#503). */
export function useTlsStatus() {
  return useQuery({
    queryKey: adminQueryKeys.club.tls(),
    queryFn: adminApi.club.getTls,
  });
}

/** Makes the HTTPS server certificate again; {@code ca} also makes the club CA again, so devices must reinstall it. */
export function useRegenerateTls() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (what: 'server' | 'ca') =>
      what === 'ca' ? adminApi.club.regenerateTlsCa() : adminApi.club.regenerateTlsServer(),
    onSuccess: (status) => {
      qc.setQueryData(adminQueryKeys.club.tls(), status);
      void qc.invalidateQueries({ queryKey: publicQueryKeys.about() });
    },
  });
}

/** Switches HTTPS on or off for the next start (#510); the app must be restarted for it to apply. */
export function useChangeTlsSetting() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ enabled, port }: { enabled: boolean; port?: number }) =>
      adminApi.club.changeTlsSetting(enabled, port),
    onSuccess: (status) => qc.setQueryData(adminQueryKeys.club.tls(), status),
  });
}

/** Downloads this install's club settings file and saves it through the browser (#510). */
export function useExportClubSettings() {
  return useMutation({
    mutationFn: adminApi.club.exportSettings,
    onSuccess: ({ blob, filename }) => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    },
  });
}

/** Previews or imports a club settings file (#510); an import refreshes everything the Club page shows. */
export function useImportClubSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ file, apply }: { file: unknown; apply: boolean }) => adminApi.club.importSettings(file, apply),
    onSuccess: (_sections, { apply }) => {
      if (apply) void qc.invalidateQueries();
    },
  });
}
