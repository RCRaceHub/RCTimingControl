export const setupQueryKeys = {
  status: () => ['setup', 'status'] as const,
  progress: () => ['setup', 'progress'] as const,
  decoderConfig: () => ['setup', 'decoder-config'] as const,
  networkName: () => ['setup', 'network-name'] as const,
};
