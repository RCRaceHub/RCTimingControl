import { useLayoutEffect, useRef, type ReactNode } from 'react';
import { QrLink } from '@/components/QrLink';

/**
 * Shrinks the frame's contents until they fit the screen's height, so a long field never runs off a 1920 × 1080 TV.
 * A frame that already fits is left at full size. The content is laid out {@code 1 / scale} wider as it shrinks, so
 * it still fills the width; a few passes settle that, since a wider layout wraps less and is shorter.
 */
function useFitToScreen<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const fit = () => {
      let scale = 1;
      for (let pass = 0; pass < 4; pass++) {
        el.style.width = `${100 / scale}%`;
        el.style.minHeight = '0';
        const next = Math.min(1, window.innerHeight / Math.max(el.offsetHeight, 1));
        if (Math.abs(next - scale) < 0.005) break;
        scale = next;
      }
      el.style.width = `${100 / scale}%`;
      el.style.minHeight = `${100 / scale}vh`;
      el.style.transform = scale < 1 ? `scale(${scale})` : '';
    };
    fit();
    const observer = new ResizeObserver(fit);
    observer.observe(el);
    window.addEventListener('resize', fit);
    return () => {
      observer.disconnect();
      window.removeEventListener('resize', fit);
    };
  }, []);
  return ref;
}

// Full-screen dark frame for a venue TV: large type, high contrast, no app chrome. It never scrolls: what would
// run past the bottom of the screen is scaled down to fit.
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
  const frame = useFitToScreen<HTMLDivElement>();
  return (
    <div className="dark h-screen overflow-hidden bg-slate-950">
    <div ref={frame} className="flex origin-top-left flex-col gap-8 p-8 text-slate-50 lg:p-12">
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
