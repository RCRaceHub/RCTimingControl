import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import type { LiveTimingSettingsDto, UpdateClubProfileRequest } from '@/lib/adminApi';
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
      void qc.invalidateQueries({ queryKey: ['public', 'privacy-notice'] });
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
      void qc.invalidateQueries({ queryKey: ['boards', 'settings'] });
    },
  });
}
