import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import { featureDriver } from '@/lib/raceControlApi';
import { getApiErrorMessage } from '@/lib/errors';

/** Sends a car to the streaming overlay's lower third (#533). Nothing is cached, as the server keeps no state. */
export function useFeatureDriver(raceId: number) {
  return useMutation({
    mutationFn: (entryId: number) => featureDriver(raceId, entryId),
    onError: err => toast.error(getApiErrorMessage(err, 'Could not show the driver on the overlay.')),
  });
}
