import { useState } from 'react';
import { AlertTriangle, ChevronLeft, ChevronRight, Pause, Play, Plus, RefreshCw, Search, Trash2, Upload, X } from 'lucide-react';
import { toast } from 'sonner';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useConfirm } from '@/components/ConfirmDialog';
import { QueryError } from '@/components/QueryError';
import { useBoardPages, useScreenMutations, useScreens } from '@/hooks/admin/useAdminScreens';
import { getApiErrorMessage } from '@/lib/errors';
import { cn } from '@/lib/utils';
import type { ControlAction, FoundScreen, Screen, ScreenActionResult } from '@/lib/screensApi';
import { MessagesPanel } from './MessagesPanel';
import { PlaylistsPanel } from './PlaylistsPanel';
import { AddScreenDialog, GotoDialog, ShowDialog, TokenDialog } from './ScreenDialogs';
import { CONNECTION, bannerBadge, messageBadge, secondsToNext, showing, stateLabel, summariseResults, supports } from './screenFormat';

type DialogName = 'add' | 'token' | 'show' | 'goto';

/**
 * The screen manager (#708): the EventScreenDisplay viewers on the venue network with their live status, and the
 * playlists pushed to them. Tick screens to send them a command as a group. The list refreshes every few seconds,
 * because the screens cannot push. Open to ADMIN and RACE_DIRECTOR.
 */
export default function ScreensPage() {
  const { data, isLoading, isError, refetch } = useScreens();
  const { data: boardPages } = useBoardPages();
  const m = useScreenMutations();
  const { confirm, dialog: confirmDialog } = useConfirm();
  const [selected, setSelected] = useState<number[]>([]);
  const [dialog, setDialog] = useState<DialogName | null>(null);

  const screens = data?.screens ?? [];
  const found = data?.found ?? [];
  const ids = selected.filter(id => screens.some(s => s.id === id));
  const none = ids.length === 0;

  function toggle(id: number) {
    setSelected(sel => (sel.includes(id) ? sel.filter(x => x !== id) : [...sel, id]));
  }

  /** Tells the official how a command to a group went: success, or which screens refused and why. */
  function report(results: ScreenActionResult[], done: string) {
    const summary = summariseResults(results);
    if (summary.ok) toast.success(done);
    else toast.error(summary.text);
  }

  async function run<T>(action: () => Promise<T>, fallback: string, then?: (result: T) => void) {
    try {
      then?.(await action());
    } catch (e) {
      toast.error(getApiErrorMessage(e, fallback));
    }
  }

  async function addFound(f: FoundScreen) {
    await run(() => m.add.mutateAsync({ host: f.host, port: f.port, token: undefined }), 'Could not add the screen.',
      s => toast.success(`Added ${s.name}`));
  }

  const control = (action: ControlAction, done: string, page?: number) =>
    run(() => m.control.mutateAsync({ action, ids, page }), 'Could not send that to the screens.', r => report(r, done));

  async function handleRemove(screen: Screen) {
    const confirmed = await confirm({
      title: `Forget ${screen.name}?`,
      description: 'It keeps showing its playlist and still accepts the token it has, because only the screen itself can '
        + 'change that. To lock it out, change its api_token. It shows under "Found, not added" again if it is on the network.',
      confirmLabel: 'Forget',
      destructive: true,
    });
    if (!confirmed) return;
    await run(() => m.remove.mutateAsync(screen.id), 'Could not forget the screen.', () => toast.success('Screen forgotten'));
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
        <h1 className="text-2xl font-semibold">Screens</h1>
        <div className="flex flex-wrap gap-2">
          {data?.discoveryEnabled && (
            <Button variant="outline" disabled={m.scan.isPending}
              onClick={() => void run(() => m.scan.mutateAsync(), 'Could not scan the network.', () => toast.success('Looking for screens…'))}>
              <Search className="h-4 w-4 mr-1" />Scan network
            </Button>
          )}
          <Button onClick={() => setDialog('add')}><Plus className="h-4 w-4 mr-1" />Add screen</Button>
        </div>
      </div>
      <p className="text-sm text-muted-foreground mb-4 max-w-2xl">
        The Raspberry Pi screens around the venue. Tick one or more to control them together. Screens are
        found on the network{data && !data.discoveryEnabled ? ' (switched off here: add them by hand)' : ''} and
        wait under "Found, not added" until you add them. One that goes quiet stays in the list as offline.
      </p>

      {found.length > 0 && (
        <div className="mb-4 rounded-lg border p-3" role="region" aria-label="Found, not added">
          <h2 className="text-sm font-medium mb-1">Found, not added</h2>
          <p className="text-xs text-muted-foreground mb-2">
            Anyone on the network can announce a screen, so check the name and address are one of yours before adding it.
          </p>
          <ul className="space-y-1">
            {found.map(f => (
              <li key={f.screenId} className="flex flex-wrap items-center gap-2 text-sm">
                <span className="font-medium">{f.name}</span>
                <span className="text-xs text-muted-foreground font-mono">{f.screenId}</span>
                <span className="text-xs text-muted-foreground">{f.host}:{f.port}{f.authRequired ? ' · asks for a token' : ''}</span>
                <Button size="sm" variant="outline" className="ml-auto" disabled={m.add.isPending}
                  onClick={() => void addFound(f)}>Add</Button>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-2 mb-3" role="toolbar" aria-label="Control the ticked screens">
        <span className="text-sm text-muted-foreground mr-1">{none ? 'Tick screens to control them' : `${ids.length} ticked`}</span>
        <Button variant="outline" size="sm" disabled={none} onClick={() => void control('previous', 'Went back')}>
          <ChevronLeft className="h-4 w-4 mr-1" />Previous
        </Button>
        <Button variant="outline" size="sm" disabled={none} onClick={() => void control('next', 'Moved on')}>
          Next<ChevronRight className="h-4 w-4 ml-1" />
        </Button>
        <Button variant="outline" size="sm" disabled={none} onClick={() => setDialog('goto')}>Go to page…</Button>
        <Button variant="outline" size="sm" disabled={none} onClick={() => void control('pause', 'Paused')}>
          <Pause className="h-4 w-4 mr-1" />Pause
        </Button>
        <Button variant="outline" size="sm" disabled={none} onClick={() => void control('resume', 'Resumed')}>
          <Play className="h-4 w-4 mr-1" />Resume
        </Button>
        <Button variant="outline" size="sm" disabled={none} onClick={() => void control('reload', 'Reloaded')}>
          <RefreshCw className="h-4 w-4 mr-1" />Reload
        </Button>
        <Button variant="outline" size="sm" disabled={none} onClick={() => setDialog('show')}>
          <Upload className="h-4 w-4 mr-1" />Show a page…
        </Button>
        <Button variant="outline" size="sm" disabled={none}
          onClick={() => void run(() => m.clearShow.mutateAsync(ids), 'Could not clear the page.', r => report(r, 'Back to the playlists'))}>
          <X className="h-4 w-4 mr-1" />Clear page
        </Button>
        <Button variant="outline" size="sm" disabled={none} onClick={() => setDialog('token')}>Set token…</Button>
      </div>

      {isError && <QueryError variant="banner" message="Failed to load the screens." onRetry={refetch} />}

      {isLoading ? (
        <div className="space-y-2">
          {[...Array(3)].map((_, i) => <div key={i} className="h-14 rounded-lg bg-muted animate-pulse" />)}
        </div>
      ) : (
        <div className="rounded-lg border overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-10">
                  <Checkbox aria-label="Tick all screens" checked={screens.length > 0 && ids.length === screens.length}
                    onCheckedChange={c => setSelected(c ? screens.map(s => s.id) : [])} />
                </TableHead>
                <TableHead>Screen</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Showing</TableHead>
                <TableHead>Next in</TableHead>
                <TableHead>Playlist</TableHead>
                <TableHead>Browser</TableHead>
                <TableHead className="w-24" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {screens.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8} className="text-sm text-muted-foreground">
                    No screens yet. Switch a screen on, scan the network, or add it by hand.
                  </TableCell>
                </TableRow>
              )}
              {screens.map(screen => (
                <ScreenRow key={screen.id} screen={screen} ticked={selected.includes(screen.id)}
                  onToggle={() => toggle(screen.id)} onRemove={() => void handleRemove(screen)}
                  onRepush={() => void run(() => m.repush.mutateAsync(screen.id), 'Could not push again.', r => report(r, 'Playlist pushed again'))} />
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <PlaylistsPanel selectedIds={ids} onPushed={r => report(r, 'Playlist pushed')} />

      <MessagesPanel screens={screens} selectedIds={ids} />

      {dialog === 'add' && (
        <AddScreenDialog open onOpenChange={o => { if (!o) setDialog(null); }}
          onSubmit={async v => {
            await run(() => m.add.mutateAsync(v), 'Could not add the screen.', s => {
              toast.success(`Added ${s.name}`);
              setDialog(null);
            });
          }} />
      )}
      {dialog === 'token' && (
        <TokenDialog open onOpenChange={o => { if (!o) setDialog(null); }}
          screens={screens.filter(s => ids.includes(s.id)).map(s => ({ id: s.id, name: s.name }))}
          onSubmitEach={async tokens => {
            await run(async () => {
              for (const t of tokens) await m.setToken.mutateAsync({ ids: [t.id], token: t.token });
            }, 'Could not save the tokens.', () => {
              toast.success('Tokens saved');
              setDialog(null);
            });
          }}
          onSubmit={async token => {
            await run(() => m.setToken.mutateAsync({ ids, token }), 'Could not save the token.', () => {
              toast.success(token ? 'Token saved' : 'Token cleared');
              setDialog(null);
            });
          }} />
      )}
      {dialog === 'show' && (
        <ShowDialog open onOpenChange={o => { if (!o) setDialog(null); }} count={ids.length} boardPages={boardPages}
          onSubmit={async v => {
            await run(() => m.show.mutateAsync({ ids, ...v }), 'Could not show the page.', r => {
              report(r, 'Page shown');
              setDialog(null);
            });
          }} />
      )}
      {dialog === 'goto' && (
        <GotoDialog open onOpenChange={o => { if (!o) setDialog(null); }}
          onSubmit={async page => {
            await run(() => m.control.mutateAsync({ action: 'goto', ids, page }), 'Could not move the screens.', r => {
              report(r, `Went to page ${page}`);
              setDialog(null);
            });
          }} />
      )}
      {confirmDialog}
    </div>
  );
}

/**
 * What is up on a screen besides its playlist: the message (style, title, time left; an open-ended one is shaded
 * as a warning because it is easy to forget), the banner (with a warning when the screen cannot draw it over the page
 * showing), and a note on a screen whose agent is too old for either.
 */
function MessageBadges({ screen }: { screen: Screen }) {
  const message = messageBadge(screen);
  const banner = bannerBadge(screen);
  return (
    <div className="mt-1 space-y-1 whitespace-normal">
      {message && (
        <Badge variant="outline" className={cn('border-transparent h-auto whitespace-normal text-left',
          message.openEnded ? 'bg-warning/15 text-warning' : 'bg-info/15 text-info')}>
          {message.openEnded && <AlertTriangle className="h-3.5 w-3.5 shrink-0 mr-1" aria-hidden />}{message.text}
        </Badge>
      )}
      {banner && (
        <div>
          <Badge variant="outline" className="border-transparent h-auto whitespace-normal text-left bg-info/15 text-info">{banner.text}</Badge>
          {banner.hidden && (
            <div className="mt-1 flex items-start gap-1 text-xs text-warning max-w-64">
              <AlertTriangle className="h-3.5 w-3.5 shrink-0 mt-px" />
              This screen cannot draw the banner over the page it is showing
            </div>
          )}
        </div>
      )}
      {(!supports(screen, 'message') || !supports(screen, 'banner')) && (
        <div className="text-xs text-muted-foreground">Update this screen to 0.6.0 for messages and banners</div>
      )}
    </div>
  );
}

function ScreenRow({ screen, ticked, onToggle, onRemove, onRepush }: {
  screen: Screen;
  ticked: boolean;
  onToggle: () => void;
  onRemove: () => void;
  onRepush: () => void;
}) {
  const connection = CONNECTION[screen.connection];
  const next = secondsToNext(screen);
  const live = screen.live;
  const stale = screen.connection === 'UNREACHABLE' || screen.connection === 'OFFLINE';
  const problems = [...screen.warnings, ...(screen.connection === 'OFFLINE' && screen.error ? [screen.error] : [])];

  return (
    <TableRow data-state={ticked ? 'selected' : undefined}>
      <TableCell className="align-top">
        <Checkbox aria-label={`Tick ${screen.name}`} checked={ticked} onCheckedChange={onToggle} />
      </TableCell>
      <TableCell className="align-top">
        <div className="font-medium">{screen.name}</div>
        <div className="text-xs text-muted-foreground font-mono">{screen.screenId}</div>
        <div className="text-xs text-muted-foreground">{screen.host}:{screen.port}{screen.agentVersion ? ` · v${screen.agentVersion}` : ''}</div>
        {screen.hasToken && <div className="text-xs text-muted-foreground">Token saved</div>}
      </TableCell>
      <TableCell className="align-top">
        <Badge variant="outline" className={cn('border-transparent', connection.className)}>{connection.label}</Badge>
        {screen.connection === 'OFFLINE' && screen.lastSeenAt && (
          <div className="text-xs text-muted-foreground mt-1">Last seen {new Date(screen.lastSeenAt).toLocaleString()}</div>
        )}
        {problems.map((w, i) => (
          <div key={`${i}-${w}`} className="mt-1 flex items-start gap-1 text-xs text-warning max-w-64">
            <AlertTriangle className="h-3.5 w-3.5 shrink-0 mt-px" />{w}
          </div>
        ))}
        {screen.drifted && (
          <div className="mt-1">
            <Badge variant="outline" className="border-transparent bg-warning/15 text-warning">Changed outside the manager</Badge>
            {screen.playlistId != null && (
              <Button variant="link" size="sm" className="px-1 h-auto" onClick={onRepush}>Push again</Button>
            )}
          </div>
        )}
      </TableCell>
      <TableCell className={cn('align-top text-sm max-w-64', stale && 'text-muted-foreground')}>
        {live ? (
          <>
            <div>{stateLabel(screen)}</div>
            <div className="truncate text-muted-foreground" title={showing(screen)}>{showing(screen)}</div>
            <MessageBadges screen={screen} />
          </>
        ) : '—'}
      </TableCell>
      <TableCell className="align-top text-sm">
        {!stale && next != null ? `${next} s` : '—'}
      </TableCell>
      <TableCell className="align-top text-sm">
        {screen.playlistName ?? (live?.playlistLength ? 'Set elsewhere' : '—')}
        {live?.playlistRevision != null && (
          <div className="text-xs text-muted-foreground">revision {live.playlistRevision}, {live.playlistLength ?? 0} page(s)</div>
        )}
      </TableCell>
      <TableCell className="align-top text-sm">
        {live ? (live.browserRunning ? 'Running' : <span className="text-destructive">Not running</span>) : '—'}
        {live?.browserRestarts ? <div className="text-xs text-warning">{live.browserRestarts} restart(s)</div> : null}
        {live?.pageReachable === false && <div className="text-xs text-destructive">Cannot reach the page</div>}
        {screen.clockSkewSeconds != null && Math.abs(screen.clockSkewSeconds) > 1 && (
          <div className="text-xs text-muted-foreground">Clock {screen.clockSkewSeconds > 0 ? '+' : ''}{Math.round(screen.clockSkewSeconds)} s</div>
        )}
      </TableCell>
      <TableCell className="align-top">
        <Button variant="ghost" size="icon-sm" aria-label={`Forget ${screen.name}`} onClick={onRemove}>
          <Trash2 className="h-4 w-4 text-destructive" />
        </Button>
      </TableCell>
    </TableRow>
  );
}
