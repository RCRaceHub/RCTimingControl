import { useQuery } from '@tanstack/react-query';
import { getBoardSettings, type BoardSettingsDto } from '@/lib/boardsApi';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';
import { DEFAULT_CLOSE_MS, LATE_AFTER_AVERAGE_MS } from '@/lib/lapProgress';

/**
 * The club's thresholds a screen draws with (#505): how near the car ahead is shown as close, and when a lap bar
 * turns red. The defaults stand in until the answer arrives, or if the server cannot be reached, so a screen never
 * waits on it.
 */
/**
 * Whether the club's time precision has been read (or could not be), and whether times are shown to hundredths. The
 * root layout holds the page back until it is ready, so a club on hundredths never flashes thousandths.
 */
export function useTimePrecision(): { ready: boolean; hundredths: boolean } {
  const { data, isPending } = useQuery({
    queryKey: publicQueryKeys.boards.settings(),
    queryFn: () => getBoardSettings(),
    staleTime: 60_000,
  });
  return { ready: !isPending, hundredths: data?.truncateToHundredths ?? false };
}

export function useBoardSettings(): BoardSettingsDto {
  const { data } = useQuery({
    queryKey: publicQueryKeys.boards.settings(),
    queryFn: () => getBoardSettings(),
    staleTime: 60_000,
  });
  return data ?? { closeGapMs: DEFAULT_CLOSE_MS, lateMarginMs: LATE_AFTER_AVERAGE_MS,
    truncateToHundredths: false, hiddenRowIcons: [] };
}
