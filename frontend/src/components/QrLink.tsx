import { useQuery } from '@tanstack/react-query';
import { QRCodeSVG } from 'qrcode.react';
import { getAboutInfo } from '@/lib/api';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';

const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]']);

/**
 * The address a phone on the venue network can open for {@code path}. A screen opened on the timing laptop itself
 * is at "localhost", which means nothing to a phone, so the laptop's network address stands in for it.
 */
export function shareableUrl(path: string, networkAddresses: string[] | undefined): string {
  const { origin, hostname } = window.location;
  const base = LOCAL_HOSTS.has(hostname) && networkAddresses && networkAddresses.length > 0
    ? networkAddresses[0].replace(/\/+$/, '')
    : origin;
  return `${base}${path}`;
}

type Props = {
  /** The page to open, such as "/qualifying?event=3". */
  path: string;
  /** What scanning leads to, shown under the code. */
  caption: string;
  size?: number;
  /** Light caption text for a dark board. */
  onDark?: boolean;
};

/** A QR code to a page on this server, for a screen or a printout that people scan with a phone. */
export function QrLink({ path, caption, size = 128, onDark = false }: Props) {
  const { data } = useQuery({
    queryKey: publicQueryKeys.about(),
    queryFn: getAboutInfo,
    staleTime: Infinity,
  });
  const url = shareableUrl(path, data?.addresses);

  return (
    <figure className="flex flex-col items-center gap-2">
      {/* White quiet zone so the code scans on a dark board too */}
      <div className="rounded bg-white p-2">
        <QRCodeSVG value={url} size={size} role="img" aria-label={`QR code: ${caption}`} />
      </div>
      <figcaption className={`text-center text-sm ${onDark ? 'text-slate-300' : 'text-muted-foreground'}`}>
        {caption}
      </figcaption>
    </figure>
  );
}
