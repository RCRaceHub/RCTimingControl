import { useLayoutEffect, useRef, useState } from 'react';
import { Copy } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  DEFAULT_OVERLAY_DESIGN, GAP_EVERY_LIMIT, OVERLAY_TOP_LIMIT, TICKER_ITEMS, overlayQuery,
  type OverlayDesign, type TickerItem,
} from '@/pages/boards/boardFormat';

const TICKER_LABELS: Record<TickerItem, string> = {
  next: 'Next race',
  drivers: 'Drivers on the grid',
  marshals: 'Marshals',
  tq: 'Top qualifiers',
  fastest: 'Fastest laps',
};

/** The size the overlay is drawn at on stream, so the preview shows it as OBS will. */
const STREAM_W = 1920;
const STREAM_H = 1080;

function Check({ label, checked, onChange }: { label: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="flex items-center gap-2 text-sm">
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      {label}
    </label>
  );
}

/**
 * The overlay designer (#524): an official picks the streaming overlay's parts, layout and theme, sees it live in a
 * preview, and copies the address to paste into OBS as a browser source. Nothing is stored on the server: the address
 * carries the design, so any number of looks need no setup. Open to ADMIN and RACE_DIRECTOR.
 */
export default function OverlayDesignerPage() {
  const [design, setDesign] = useState<OverlayDesign>(DEFAULT_OVERLAY_DESIGN);
  const set = <K extends keyof OverlayDesign>(key: K, value: OverlayDesign[K]) =>
    setDesign((d) => ({ ...d, [key]: value }));

  // The typed text of the two number fields, kept apart from the design so half-typed values aren't rewritten
  const [topText, setTopText] = useState(String(DEFAULT_OVERLAY_DESIGN.top));
  const [eventText, setEventText] = useState('');
  const [gapEveryText, setGapEveryText] = useState(String(DEFAULT_OVERLAY_DESIGN.gapEverySeconds));

  const query = overlayQuery(design);
  const path = `/boards/overlay${query}`;
  const address = `${window.location.origin}${path}`;
  const isLocalHost = ['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname);

  const frame = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(0.4);
  useLayoutEffect(() => {
    const fit = () => {
      const width = frame.current?.clientWidth;
      if (width) setScale(width / STREAM_W);
    };
    fit();
    if (typeof ResizeObserver === 'undefined') {
      window.addEventListener('resize', fit);
      return () => window.removeEventListener('resize', fit);
    }
    const observer = new ResizeObserver(fit);
    if (frame.current) observer.observe(frame.current);
    return () => observer.disconnect();
  }, []);

  async function copy() {
    try {
      await navigator.clipboard.writeText(address);
      toast.success('Address copied');
    } catch {
      toast.error('Could not copy. Select the address and copy it by hand.');
    }
  }

  function toggleTicker(item: TickerItem, on: boolean) {
    set('tickerItems', on ? [...design.tickerItems, item] : design.tickerItems.filter((i) => i !== item));
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Overlay designer</h1>
        <p className="text-sm text-muted-foreground">
          Choose what the streaming overlay shows, then copy its address into OBS as a browser source (1920 × 1080,
          transparent background). The preview follows the race on track.
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-[22rem_1fr]">
        <div className="space-y-5">
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">Timing</legend>
            <div className="space-y-1.5">
              <Label htmlFor="ov-layout">Layout</Label>
              <select
                id="ov-layout"
                className="w-full rounded-md border bg-background px-3 py-2 text-sm"
                value={design.layout}
                onChange={(e) => set('layout', e.target.value as OverlayDesign['layout'])}
              >
                <option value="tower">Timing tower</option>
                <option value="table">Table</option>
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ov-gap">Gap column (tower)</Label>
              <select
                id="ov-gap"
                className="w-full rounded-md border bg-background px-3 py-2 text-sm"
                value={design.gap}
                onChange={(e) => set('gap', e.target.value as OverlayDesign['gap'])}
              >
                <option value="int">INT: gap to the car ahead</option>
                <option value="leader">GAP: gap to the leader</option>
                <option value="auto">Take turns</option>
              </select>
            </div>
            {design.gap === 'auto' && (
              <div className="space-y-1.5">
                <Label htmlFor="ov-gap-every">Seconds on each</Label>
                <Input
                  id="ov-gap-every"
                  type="number"
                  min={1}
                  max={GAP_EVERY_LIMIT}
                  value={gapEveryText}
                  onChange={(e) => {
                    setGapEveryText(e.target.value);
                    const n = Math.round(Number(e.target.value));
                    if (e.target.value.trim() !== '' && Number.isFinite(n)) {
                      set('gapEverySeconds', Math.min(GAP_EVERY_LIMIT, Math.max(1, n)));
                    }
                  }}
                  onBlur={() => setGapEveryText(String(design.gapEverySeconds))}
                />
              </div>
            )}
            <div className="space-y-1.5">
              <Label htmlFor="ov-top">Places shown</Label>
              <Input
                id="ov-top"
                type="number"
                min={1}
                max={OVERLAY_TOP_LIMIT}
                value={topText}
                onChange={(e) => {
                  setTopText(e.target.value);
                  const n = Math.round(Number(e.target.value));
                  if (e.target.value.trim() !== '' && Number.isFinite(n)) {
                    set('top', Math.min(OVERLAY_TOP_LIMIT, Math.max(1, n)));
                  }
                }}
                onBlur={() => setTopText(String(design.top))}
              />
            </div>
            <Check label="Class name" checked={design.showClass} onChange={(v) => set('showClass', v)} />
            <Check label="Predicted laps" checked={design.showPredicted} onChange={(v) => set('showPredicted', v)} />
            <Check label="Qualifying position chip" checked={design.showQualifying} onChange={(v) => set('showQualifying', v)} />
            <Check label="Lap progress bars" checked={design.showBars} onChange={(v) => set('showBars', v)} />
            <Check label="Places gained arrows" checked={design.showArrows} onChange={(v) => set('showArrows', v)} />
            <Check label="Battles" checked={design.showBattles} onChange={(v) => set('showBattles', v)} />
            <Check label="Position chart" checked={design.showChart} onChange={(v) => set('showChart', v)} />
          </fieldset>

          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">Ticker</legend>
            {TICKER_ITEMS.map((item) => (
              <Check
                key={item}
                label={TICKER_LABELS[item]}
                checked={design.tickerItems.includes(item)}
                onChange={(v) => toggleTicker(item, v)}
              />
            ))}
          </fieldset>

          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">Look</legend>
            <Check label="Sponsor bar" checked={design.showSponsors} onChange={(v) => set('showSponsors', v)} />
            <Check label="Light theme" checked={design.theme === 'light'} onChange={(v) => set('theme', v ? 'light' : 'dark')} />
          </fieldset>

          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">Event</legend>
            <div className="space-y-1.5">
              <Label htmlFor="ov-event">Event ID (blank follows the event racing now)</Label>
              <Input
                id="ov-event"
                inputMode="numeric"
                value={eventText}
                onChange={(e) => {
                  setEventText(e.target.value);
                  const n = Number(e.target.value);
                  set('eventId', e.target.value.trim() !== '' && Number.isSafeInteger(n) && n > 0 ? n : null);
                }}
              />
            </div>
          </fieldset>

          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setDesign(DEFAULT_OVERLAY_DESIGN);
              setTopText(String(DEFAULT_OVERLAY_DESIGN.top));
              setEventText('');
              setGapEveryText(String(DEFAULT_OVERLAY_DESIGN.gapEverySeconds));
            }}
          >
            Reset to the default
          </Button>
        </div>

        <div className="min-w-0 space-y-4">
          <div
            ref={frame}
            className="relative w-full overflow-hidden rounded-lg border bg-neutral-700"
            style={{ height: STREAM_H * scale }}
          >
            <iframe
              key={path}
              title="Overlay preview"
              src={path}
              className="absolute left-0 top-0 border-0"
              style={{ width: STREAM_W, height: STREAM_H, transform: `scale(${scale})`, transformOrigin: 'top left' }}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="ov-address">Address for OBS</Label>
            {isLocalHost && (
              <p className="text-xs text-muted-foreground">
                This page is open on <code>{window.location.hostname}</code>, which another PC can't reach. If OBS runs
                on a different computer, swap it for the laptop's address on the venue network (see About).
              </p>
            )}
            <div className="flex gap-2">
              <Input id="ov-address" readOnly value={address} onFocus={(e) => e.currentTarget.select()} />
              <Button onClick={copy}>
                <Copy className="mr-2 h-4 w-4" />
                Copy
              </Button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
