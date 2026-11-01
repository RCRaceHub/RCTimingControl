import { cn } from '@/lib/utils';
import type { MessageLevel, MessageStyle } from '@/lib/screensApi';
import { levelColour } from './screenFormat';

/**
 * Roughly how a message will look on a screen: the viewer's own panel colour for the level, a very large white title and
 * the text under it. A hold pulses slowly, as it does on the screen. The colours are the viewer's fixed ones, not theme
 * tokens, because the preview shows another device's display.
 */
export function MessagePreview({ title, text, style, level }: {
  title: string;
  text: string;
  style: MessageStyle;
  level: MessageLevel;
}) {
  return (
    <div role="img" aria-label="Preview of the message on a screen"
      className="flex aspect-video w-full max-w-md flex-col items-center justify-center gap-2 overflow-hidden rounded-md p-4 text-center"
      style={{ backgroundColor: levelColour(level), color: '#ffffff' }}>
      <div className={cn('text-2xl font-bold leading-tight break-words', style === 'hold' && 'animate-pulse')}>
        {title.trim() || 'Title'}
      </div>
      {text.trim() && <div className="text-sm opacity-90 whitespace-pre-line break-words">{text}</div>}
    </div>
  );
}

/** Roughly how a banner will look: a strip of the level's colour with the text in white. */
export function BannerPreview({ text, level }: { text: string; level: MessageLevel }) {
  return (
    <div role="img" aria-label="Preview of the banner on a screen"
      className="w-full max-w-md truncate rounded-md px-3 py-2 text-sm font-medium"
      style={{ backgroundColor: levelColour(level), color: '#ffffff' }}>
      {text.trim() || 'Banner text'}
    </div>
  );
}
