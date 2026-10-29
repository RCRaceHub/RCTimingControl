import { useLayoutEffect, useRef, useState } from 'react';
import { Copy } from 'lucide-react';
import { toast } from 'sonner';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { Button } from '@/components/ui/button';
import { adminQueryKeys } from '@/hooks/admin/adminQueryKeys';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';
import { useConfirm } from '@/components/ConfirmDialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  DEFAULT_OVERLAY_DESIGN, GAP_EVERY_LIMIT, OVERLAY_TOP_LIMIT, TICKER_ITEMS, overlayDesignFromTheme, overlayQuery,
  parseBattleStyle, parseLowerThird, parseSeparatorImage, themeDesignToSave, type OverlayDesign, type TickerItem,
} from '@/pages/boards/boardFormat';
import type { OverlayThemeDto } from '@/lib/boardsApi';
import { overlayThemesAdminApi } from '@/lib/overlayThemesApi';
import { OverlayLayoutEditor } from './OverlayLayoutEditor';
import type { OverlayComponent } from '@/pages/boards/overlayLayout';
import { SponsorListEditor } from '@/components/SponsorListEditor';
import { useThemeSponsorMutations, useThemeSponsors } from '@/hooks/admin/useThemeSponsors';
import { getApiErrorMessage } from '@/lib/errors';

const TICKER_LABELS: Record<TickerItem, string> = {
  next: 'Next race',
  drivers: 'Drivers on the grid',
  marshals: 'Marshals',
  tq: 'Top qualifiers',
  fastest: 'Fastest laps',
  gain: 'Most places gained',
  loss: 'Most places lost',
  pace: 'Best 3 and 5 lap average',
  flying: 'Flying lap',
  improved: 'Most improved on last heat',
  steady: 'Most consistent',
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
 * The sponsors a saved theme owns. An overlay opened with the theme (`?look=ID`) shows these in its sponsor bar
 * instead of the event's; with none, it shows the event's. They belong to the saved theme, so they change as soon as
 * they are edited here, not when the theme is saved.
 */
function ThemeSponsors({ themeId }: { themeId: number }) {
  const { data: sponsors = [], isLoading } = useThemeSponsors(themeId);
  return (
    <SponsorListEditor
      sponsors={sponsors}
      isLoading={isLoading}
      mutations={useThemeSponsorMutations(themeId)}
      canEdit
      intro="An overlay using this theme shows these in its sponsor bar instead of the event's sponsors; with none here it shows the event's."
    />
  );
}

/**
 * The overlay designer (#524): an official picks the streaming overlay's parts, layout and look, sees it live in a
 * preview, and copies the address to paste into OBS as a browser source. The address carries the design, so a look can
 * be used with no setup. A design can also be saved as a named **theme** (the club's themes start with BRCA and Club):
 * the address of an unchanged saved theme is `?look=ID`, so editing the theme later changes every overlay that uses
 * it. A theme holds everything but the event. One theme can be the club's default, which the plain overlay link opens.
 * Open to ADMIN and RACE_DIRECTOR.
 */
export default function OverlayDesignerPage() {
  const { confirm, dialog } = useConfirm();
  const [design, setDesign] = useState<OverlayDesign>(DEFAULT_OVERLAY_DESIGN);
  const set = <K extends keyof OverlayDesign>(key: K, value: OverlayDesign[K]) =>
    setDesign((d) => ({ ...d, [key]: value }));

  // The typed text of the two number fields, kept apart from the design so half-typed values aren't rewritten
  const [topText, setTopText] = useState(String(DEFAULT_OVERLAY_DESIGN.top));
  const [eventText, setEventText] = useState('');
  const [gapEveryText, setGapEveryText] = useState(String(DEFAULT_OVERLAY_DESIGN.gapEverySeconds));
  const [separatorText, setSeparatorText] = useState('');

  const queryClient = useQueryClient();
  const { data: themes = [] } = useQuery({ queryKey: adminQueryKeys.overlayThemes.all(), queryFn: overlayThemesAdminApi.list });
  const [themeId, setThemeId] = useState<number | null>(null);
  const [themeName, setThemeName] = useState('');
  const theme = themes.find((t) => t.id === themeId) ?? null;
  const savedDesign = (t: OverlayThemeDto) => JSON.stringify(themeDesignToSave(overlayDesignFromTheme(t.design)));
  const unsaved = theme !== null && savedDesign(theme) !== JSON.stringify(themeDesignToSave(overlayDesignFromTheme(design)));

  function fillFields(next: OverlayDesign) {
    setTopText(String(next.top));
    setGapEveryText(String(next.gapEverySeconds));
    setSeparatorText(next.separatorImage ?? '');
  }

  function pick(id: number | null) {
    setThemeId(id);
    const chosen = themes.find((t) => t.id === id);
    if (!chosen) return;
    // Keeps the event typed in, as a theme does not pick one
    const next = { ...overlayDesignFromTheme(chosen.design), eventId: design.eventId };
    setDesign(next);
    fillFields(next);
    setThemeName(chosen.name);
  }

  const saveTheme = useMutation({
    mutationFn: async ({ id, name, makeDefault = false }: { id: number | null; name: string; makeDefault?: boolean }) => {
      const body = { name, design: themeDesignToSave(design) };
      const saved = id === null ? await overlayThemesAdminApi.create(body) : await overlayThemesAdminApi.update(id, body);
      return makeDefault ? overlayThemesAdminApi.setDefault(saved.id) : saved;
    },
    onSuccess: (saved, { makeDefault }) => {
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.overlayThemes.all() });
      queryClient.invalidateQueries({ queryKey: publicQueryKeys.boards.defaultOverlayTheme() });
      setThemeId(saved.id);
      setThemeName(saved.name);
      toast.success(makeDefault ? `Saved ${saved.name} as the default theme` : `Saved the theme ${saved.name}`);
    },
    onError: (e) => toast.error(getApiErrorMessage(e, 'Could not save the theme')),
  });
  const clearDefault = useMutation({
    mutationFn: () => overlayThemesAdminApi.clearDefault(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.overlayThemes.all() });
      queryClient.invalidateQueries({ queryKey: publicQueryKeys.boards.defaultOverlayTheme() });
      toast.success('The plain overlay link no longer uses a theme');
    },
    onError: (e) => toast.error(getApiErrorMessage(e, 'Could not clear the default theme')),
  });
  const duplicateTheme = useMutation({
    mutationFn: (source: OverlayThemeDto) => {
      // Copies the design as it stands in the designer, edits included
      const names = new Set(themes.map((t) => t.name));
      let name = `${source.name} copy`;
      for (let n = 2; names.has(name); n++) name = `${source.name} copy ${n}`;
      return overlayThemesAdminApi.create({ name, design: themeDesignToSave(design) });
    },
    onSuccess: (copy) => {
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.overlayThemes.all() });
      toast.success(`Made ${copy.name}`);
      setThemeId(copy.id);
      setThemeName(copy.name);
      fillFields(design);
    },
    onError: (e) => toast.error(getApiErrorMessage(e, 'Could not duplicate the theme')),
  });
  const deleteTheme = useMutation({
    mutationFn: (id: number) => overlayThemesAdminApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.overlayThemes.all() });
      setThemeId(null);
      setThemeName('');
      toast.success('Deleted the theme');
    },
    onError: (e) => toast.error(getApiErrorMessage(e, 'Could not delete the theme')),
  });

  // The preview always shows the design as it stands; the address uses the theme's own while it is unchanged
  const query = overlayQuery(design);
  const path = `/boards/overlay${query}`;
  const addressPath = theme && !unsaved
    ? `/boards/overlay?look=${theme.id}${design.eventId !== null ? `&event=${design.eventId}` : ''}`
    : path;
  const address = `${window.location.origin}${addressPath}`;
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

  // Parts the design switches off, shown dimmed in the layout editor
  const offParts = new Set<OverlayComponent>();
  if (!design.showBattles) offParts.add('battles');
  if (!design.showChart) offParts.add('chart');
  if (design.lowerThird === 'off') offParts.add('lowerThird');
  if (design.tickerItems.length === 0) offParts.add('ticker');
  if (!design.showSponsors) offParts.add('sponsors');

  function toggleTicker(item: TickerItem, on: boolean) {
    set('tickerItems', on ? [...design.tickerItems, item] : design.tickerItems.filter((i) => i !== item));
  }

  return (
    <div className="space-y-6">
      {dialog}
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
            <legend className="text-sm font-medium">Theme</legend>
            <div className="space-y-1.5">
              <Label htmlFor="ov-theme">Saved theme</Label>
              <select
                id="ov-theme"
                className="w-full rounded-md border bg-background px-3 py-2 text-sm"
                value={themeId ?? ''}
                onChange={(e) => pick(e.target.value === '' ? null : Number(e.target.value))}
              >
                <option value="">None (design below)</option>
                {themes.map((t) => (
                  <option key={t.id} value={t.id}>{t.isDefault ? `${t.name} (default)` : t.name}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ov-theme-name">Theme name</Label>
              <Input id="ov-theme-name" value={themeName} maxLength={100} onChange={(e) => setThemeName(e.target.value)} />
            </div>
            {unsaved && (
              <p className="text-xs text-muted-foreground">
                You have changed this theme. The address below holds the design as it stands; save the theme to give
                OBS a short address that follows later edits.
              </p>
            )}
            <p className="text-xs text-muted-foreground">
              {themes.some((t) => t.isDefault)
                ? `The plain overlay link (/boards/overlay, from the Boards menu) opens ${themes.find((t) => t.isDefault)?.name}.`
                : 'No default theme: the plain overlay link shows the standard look. Save a theme as the default to change that.'}
            </p>
            <div className="flex flex-wrap gap-2">
              <Button
                size="sm"
                disabled={themeId === null || themeName.trim() === '' || saveTheme.isPending}
                onClick={() => saveTheme.mutate({ id: themeId, name: themeName.trim() })}
              >
                Save theme
              </Button>
              <Button
                size="sm"
                variant="outline"
                disabled={themeName.trim() === '' || saveTheme.isPending}
                onClick={() => saveTheme.mutate({ id: themeId, name: themeName.trim(), makeDefault: true })}
              >
                Save and make default
              </Button>
              {theme?.isDefault && (
                <Button size="sm" variant="outline" disabled={clearDefault.isPending} onClick={() => clearDefault.mutate()}>
                  Clear default
                </Button>
              )}
              <Button
                size="sm"
                variant="outline"
                disabled={themeName.trim() === '' || saveTheme.isPending}
                onClick={() => saveTheme.mutate({ id: null, name: themeName.trim() })}
              >
                Save as new
              </Button>
              <Button
                size="sm"
                variant="outline"
                disabled={theme === null || duplicateTheme.isPending}
                onClick={() => theme && duplicateTheme.mutate(theme)}
              >
                Duplicate
              </Button>
              <Button
                size="sm"
                variant="outline"
                disabled={theme === null || deleteTheme.isPending}
                onClick={async () => {
                  if (theme && await confirm({
                    title: `Delete the theme ${theme.name}?`,
                    description: 'Overlays already open keep its look until reloaded, then use their address options.',
                    confirmLabel: 'Delete',
                    destructive: true,
                  })) {
                    deleteTheme.mutate(theme.id);
                  }
                }}
              >
                Delete
              </Button>
            </div>
          </fieldset>

          <fieldset className="space-y-2" data-testid="theme-sponsors">
            <legend className="text-sm font-medium">Theme sponsors</legend>
            {theme ? (
              <ThemeSponsors key={theme.id} themeId={theme.id} />
            ) : (
              <p className="text-xs text-muted-foreground">
                Pick a saved theme to give it sponsors of its own. Without any, the overlay shows the event's sponsors.
              </p>
            )}
          </fieldset>

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
            <Check label="Class colour tags (mixed classes)" checked={design.showClassTags} onChange={(v) => set('showClassTags', v)} />
            <Check label="Driver headshots (where added)" checked={design.showHeadshots} onChange={(v) => set('showHeadshots', v)} />
            <Check label="Car colours (entry colours, else class colour)" checked={design.showCarColours} onChange={(v) => set('showCarColours', v)} />
            <Check label="Pace against the top qualifier" checked={design.showTqPace} onChange={(v) => set('showTqPace', v)} />
            <Check label="Bump-up line in finals" checked={design.showBumpLine} onChange={(v) => set('showBumpLine', v)} />
            <Check label="Lap progress bars" checked={design.showBars} onChange={(v) => set('showBars', v)} />
            <Check label="Places gained arrows" checked={design.showArrows} onChange={(v) => set('showArrows', v)} />
            <Check label="Battles" checked={design.showBattles} onChange={(v) => set('showBattles', v)} />
            <label className="flex items-center justify-between gap-2 text-sm">
              Battles shown as
              <select
                className="h-8 rounded border bg-background px-2"
                value={design.battleStyle}
                disabled={!design.showBattles}
                onChange={(e) => set('battleStyle', parseBattleStyle(e.target.value))}
              >
                <option value="panel">Bottom-left panel</option>
                <option value="rows">Highlighted row of the chasing car</option>
                <option value="both">Panel and highlighted row</option>
              </select>
            </label>
            <label className="flex items-center justify-between gap-2 text-sm">
              Featured-driver lower third
              <select
                className="h-8 rounded border bg-background px-2"
                value={design.lowerThird}
                onChange={(e) => set('lowerThird', parseLowerThird(e.target.value))}
              >
                <option value="both">Race director and battles</option>
                <option value="director">Race director only</option>
                <option value="battle">Battles only</option>
                <option value="off">Off</option>
              </select>
            </label>
            <Check label="High contrast (outlined icons with text tags)" checked={design.highContrast} onChange={(v) => set('highContrast', v)} />
            <Check label="Colour key" checked={design.showLegend} onChange={(v) => set('showLegend', v)} />
            <Check label="Position chart" checked={design.showChart} onChange={(v) => set('showChart', v)} />
          </fieldset>

          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">Layout on screen</legend>
            <p className="text-xs text-muted-foreground">
              Drag each part to the area of the screen where it should sit. Parts in the same area stack.
            </p>
            <OverlayLayoutEditor placement={design.placement} off={offParts} onChange={(p) => set('placement', p)} />
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
            <div className="space-y-1.5">
              <Label htmlFor="ov-separator">Ticker separator image (blank keeps the diamond)</Label>
              <Input
                id="ov-separator"
                placeholder="https://… or /storage/…"
                value={separatorText}
                onChange={(e) => {
                  setSeparatorText(e.target.value);
                  set('separatorImage', e.target.value);
                }}
              />
              {separatorText.trim() !== '' && parseSeparatorImage(separatorText) === null && (
                <p className="text-xs text-destructive">Use a web address (https://…) or a path on this server (/…).</p>
              )}
            </div>
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
              fillFields(DEFAULT_OVERLAY_DESIGN);
              setEventText('');
              setThemeId(null);
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
