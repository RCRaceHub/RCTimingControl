import { useQuery } from '@tanstack/react-query';
import { getBoardSettings, type BoardSettingsDto } from '@/lib/boardsApi';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';
import { DEFAULT_CLOSE_MS, LATE_AFTER_AVERAGE_MS } from '@/lib/lapProgress';

/**
 * The club's thresholds a screen draws with (#505): how near the car ahead is shown as close, and when a lap bar
 * turns red. The defaults stand in until the answer arrives, or if the server cannot be reached, so a screen never
 * waits on it.
 */
export function useBoardSettings(): BoardSettingsDto {
  const { data } = useQuery({
    queryKey: publicQueryKeys.boards.settings(),
    queryFn: () => getBoardSettings(),
    staleTime: 60_000,
  });
  return data ?? { closeGapMs: DEFAULT_CLOSE_MS, lateMarginMs: LATE_AFTER_AVERAGE_MS, hiddenRowIcons: [] };
}
