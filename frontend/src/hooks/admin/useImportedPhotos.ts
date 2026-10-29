import { useMutation, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import type { ImportedPhotoLink, ImportedPhotosResult } from '@/lib/adminApi';
import { removeBackground } from '@/lib/backgroundRemoval';
import { adminQueryKeys } from './adminQueryKeys';

/** What fetching the pictures of an import came to; {@code cutOut} and {@code cutOutFailed} count the background removals. */
export interface FetchedPhotosOutcome extends ImportedPhotosResult {
  cutOut: number;
  cutOutFailed: number;
}

async function download(url: string): Promise<File> {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`The picture could not be read back (${response.status})`);
  return new File([await response.blob()], 'photo.png', { type: 'image/png' });
}

/**
 * Fetches the pictures an entry import named (#699) and, when asked, cuts the background out of each one stored.
 * The server fetches them (the browser could not, whatever the picture host allows) and stores them as uploaded;
 * the cut-out then runs here, in the browser, with the same model as an upload (#698), and replaces the stored
 * picture through the ordinary upload endpoints. A cut-out that fails keeps the picture as fetched.
 */
export function useFetchImportedPhotos(eventId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      links: ImportedPhotoLink[];
      replaceExisting: boolean;
      removeBackground: boolean;
    }): Promise<FetchedPhotosOutcome> => {
      const result = await adminApi.importPhotos(eventId, args.links, args.replaceExisting);
      let cutOut = 0;
      let cutOutFailed = 0;
      if (args.removeBackground) {
        for (const item of result.items) {
          if (item.status !== 'STORED' || !item.storedUrl) continue;
          try {
            const file = new File([await removeBackground(await download(item.storedUrl))], 'photo.png', {
              type: 'image/png',
            });
            if (item.kind === 'HEADSHOT' && item.competitorId != null) {
              await adminApi.uploadHeadshot(item.competitorId, file);
            } else if (item.entryId != null) {
              await adminApi.uploadCarPhoto(item.entryId, file);
            }
            cutOut++;
          } catch {
            cutOutFailed++;
          }
        }
      }
      return { ...result, cutOut, cutOutFailed };
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: adminQueryKeys.events.detail(eventId) });
      qc.invalidateQueries({ queryKey: adminQueryKeys.competitors.all() });
    },
  });
}
