import { useMemo } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { getPositionHistory } from '@/lib/boardsApi';
import { livePositionSeries, type PositionSeries } from '@/lib/heatSheet';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';

/**
 * The live position chart's lines for a race on track. The server works out each car's place at the end of every
 * lap, so a board opened mid-race has the whole history; it is read again whenever the field's total laps change,
 * which is when a place can change, rather than on a timer. The last answer stays up while the next one loads.
 * Pass `enabled` false where the chart isn't shown, so the page doesn't ask for it.
 */
export function useLivePositionSeries(
  raceId: number | null,
  rows: LiveTimingRowDto[],
  enabled = true,
): PositionSeries[] {
  const totalLaps = rows.reduce((sum, row) => sum + row.lapsCompleted, 0);
  const { data: places } = useQuery({
    queryKey: publicQueryKeys.boards.positionHistory(raceId, totalLaps),
    queryFn: () => getPositionHistory(raceId!),
    enabled: enabled && raceId !== null && totalLaps > 0,
    placeholderData: keepPreviousData,
  });
  return useMemo(() => livePositionSeries(rows, places ?? []), [rows, places]);
}
