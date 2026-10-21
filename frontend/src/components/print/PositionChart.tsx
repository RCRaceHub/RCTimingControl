import type { PositionSeries } from '@/lib/heatSheet';

// Distinct on white paper and on a dark screen, and still told apart by the label at the end of each line
const COLOURS = ['#2563eb', '#16a34a', '#dc2626', '#d97706', '#9333ea', '#0891b2', '#db2777', '#65a30d', '#475569', '#ea580c'];

const WIDTH = 720;
const LEFT = 44;
const RIGHT = 84;
const TOP = 12;
const BOTTOM = 40;
const ROW = 28;
const TALL_ROW = 20;
const MAX_LABEL = 12;

/**
 * Who held which place over the heat: lap across, place down (first at the top), one line per driver labelled at its
 * right end. Plain SVG, so it prints sharply and works offline. A heat with no recorded laps draws nothing.
 */
export function PositionChart({ series }: { series: PositionSeries[] }) {
  if (series.length === 0) return null;
  const lastLap = Math.max(...series.flatMap((s) => s.points.map((p) => p.lap)));
  const firstLap = Math.min(...series.flatMap((s) => s.points.map((p) => p.lap)));
  const places = Math.max(series.length, ...series.flatMap((s) => s.points.map((p) => p.position)));
  // Crowded heats get tighter rows so the chart still fits its page
  const row = places > 12 ? TALL_ROW : ROW;
  const height = TOP + BOTTOM + places * row;
  const plotW = WIDTH - LEFT - RIGHT;
  const x = (lap: number) => LEFT + (lastLap === firstLap ? plotW / 2 : ((lap - firstLap) / (lastLap - firstLap)) * plotW);
  const y = (position: number) => TOP + (position - 1) * row + row / 2;
  const lapTicks = Array.from({ length: lastLap - firstLap + 1 }, (_, i) => firstLap + i)
    .filter((lap) => lap === firstLap || lap === lastLap || (lap - firstLap) % Math.max(1, Math.ceil((lastLap - firstLap) / 10)) === 0);

  return (
    <figure className="break-inside-avoid">
      <svg
        viewBox={`0 0 ${WIDTH} ${height}`}
        role="img"
        aria-label="Position by lap"
        className="w-full text-foreground"
      >
        {Array.from({ length: places }, (_, i) => i + 1).map((place) => (
          <g key={place}>
            <line x1={LEFT} x2={WIDTH - RIGHT} y1={y(place)} y2={y(place)} stroke="currentColor" strokeOpacity="0.12" />
            <text x={LEFT - 8} y={y(place) + 4} textAnchor="end" fontSize="12" fill="currentColor">{place}</text>
          </g>
        ))}
        {lapTicks.map((lap) => (
          <text key={lap} x={x(lap)} y={height - BOTTOM + 18} textAnchor="middle" fontSize="12" fill="currentColor">{lap}</text>
        ))}
        <text x={LEFT + plotW / 2} y={height - 6} textAnchor="middle" fontSize="12" fontWeight="bold" fill="currentColor">Lap</text>
        <text transform={`translate(12 ${TOP + (places * row) / 2}) rotate(-90)`} textAnchor="middle" fontSize="12" fontWeight="bold" fill="currentColor">Position</text>
        {series.map((s, i) => {
          const colour = COLOURS[i % COLOURS.length];
          const last = s.points[s.points.length - 1];
          const label = s.label.length > MAX_LABEL ? `${s.label.slice(0, MAX_LABEL - 1)}…` : s.label;
          return (
            <g key={s.entryId}>
              <polyline
                points={s.points.map((p) => `${x(p.lap)},${y(p.position)}`).join(' ')}
                fill="none"
                stroke={colour}
                strokeWidth="1.5"
                strokeLinejoin="round"
              />
              {s.points.length === 1 && <circle cx={x(last.lap)} cy={y(last.position)} r="3" fill={colour} />}
              <text x={x(last.lap) + 6} y={y(last.position) + 4} fontSize="11" fill={colour}>{label}</text>
            </g>
          );
        })}
      </svg>
    </figure>
  );
}
