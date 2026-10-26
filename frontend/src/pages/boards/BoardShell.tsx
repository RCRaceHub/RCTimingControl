import type { ReactNode } from 'react';
import { QrLink } from '@/components/QrLink';

// Full-screen dark frame for a venue TV: large type, high contrast, no app chrome.
export function BoardShell({
  eventName,
  eventId,
  showQr = false,
  children,
}: {
  eventName?: string | null;
  eventId?: number | null;
  /** A QR code to the qualifying standings, for people to open on their phones. */
  showQr?: boolean;
  children: ReactNode;
}) {
  return (
    <div className="flex min-h-screen flex-col gap-8 bg-slate-950 p-8 text-slate-50 lg:p-12">
      {eventName && (
        <p className="text-xl font-medium uppercase tracking-wide text-slate-400 lg:text-2xl">
          {eventName}
        </p>
      )}
      {children}
      {showQr && (
        <div className="mt-auto flex justify-end">
          <QrLink
            path={eventId ? `/results?tab=qualifying&event=${eventId}` : '/results?tab=qualifying'}
            caption="Scan for qualifying standings"
            size={112}
            onDark
          />
        </div>
      )}
    </div>
  );
}

export function BoardMessage({ children }: { children: ReactNode }) {
  return (
    <div className="flex flex-1 items-center justify-center">
      <p className="text-3xl text-slate-400 lg:text-4xl">{children}</p>
    </div>
  );
}

// Shown when a board has had no answer from the server yet; its polling keeps trying.
export function BoardUnreachable() {
  return (
    <BoardShell>
      <BoardMessage>Can’t reach the timing system. Trying again…</BoardMessage>
    </BoardShell>
  );
}
