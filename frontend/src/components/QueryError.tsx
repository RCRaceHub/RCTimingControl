import type { ReactNode } from 'react';
import { Button } from '@/components/ui/button';

interface Props {
  /** What failed, such as "Could not load the race history." */
  message: string;
  /** The query's `refetch`. */
  onRetry: () => unknown;
  /**
   * Where it sits: `inline` is a line in a panel or dialog, `banner` a bar above a list that may still show old
   * rows, `page` the whole page when nothing loaded. Defaults to `inline`.
   */
  variant?: 'inline' | 'banner' | 'page';
  /** Extra actions after Retry, such as a Back button on a `page`. */
  children?: ReactNode;
}

/** What a failed query shows: the message and a Retry button that runs the query again. */
export function QueryError({ message, onRetry, variant = 'inline', children }: Props) {
  const retry = () => { void onRetry(); };
  if (variant === 'page') {
    return (
      <div role="alert" className="flex flex-col items-center justify-center py-20 text-center gap-4">
        <p className="text-muted-foreground">{message}</p>
        <Button variant="outline" onClick={retry}>Retry</Button>
        {children}
      </div>
    );
  }
  if (variant === 'banner') {
    return (
      <div role="alert" className="rounded-lg border border-destructive/30 bg-destructive/5 p-4 flex items-center justify-between mb-4">
        <p className="text-sm text-destructive">{message}</p>
        <Button variant="outline" size="sm" onClick={retry}>Retry</Button>
        {children}
      </div>
    );
  }
  return (
    <div role="alert" className="text-sm text-destructive">
      {message}{' '}
      <Button variant="link" size="sm" className="h-auto p-0 align-baseline" onClick={retry}>Retry</Button>
      {children}
    </div>
  );
}
