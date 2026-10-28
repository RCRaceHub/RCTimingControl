import { useNavigate } from 'react-router-dom';
import { Flag, ArrowRight } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { useAdminEventsList } from '@/hooks/admin/useAdminEvents';
import { formatEventDate } from '@/lib/dates';

export default function RaceControlSelectPage() {
  const navigate = useNavigate();
  const { data: events, isLoading } = useAdminEventsList();

  const active = events?.filter(e => e.status === 'IN_PROGRESS') ?? [];

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold">Race Control</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Select an in-progress event to open the race control cockpit.
        </p>
      </div>

      {isLoading && (
        <div className="space-y-3">
          {[...Array(2)].map((_, i) => (
            <div key={i} className="h-20 rounded-lg bg-muted animate-pulse" />
          ))}
        </div>
      )}

      {!isLoading && active.length === 0 && (
        <div className="flex flex-col items-center justify-center py-24 text-center">
          <Flag className="h-10 w-10 text-muted-foreground mb-4" />
          <h2 className="text-lg font-semibold mb-1">No events in progress</h2>
          <p className="text-sm text-muted-foreground mb-6">
            Move an event to <strong>In Progress</strong> from the Events page first.
          </p>
          <Button variant="outline" onClick={() => navigate('/admin/events')}>
            Go to Events
          </Button>
        </div>
      )}

      {!isLoading && active.length > 0 && (
        <div className="space-y-3 max-w-xl">
          {active.map(event => (
            <button
              key={event.id}
              onClick={() => navigate(`/race-control/event/${event.id}`)}
              className="w-full flex items-center justify-between rounded-lg border bg-card px-5 py-4 text-left hover:bg-muted/50 transition-colors group"
            >
              <div>
                <p className="font-medium">{event.name}</p>
                <p className="text-sm text-muted-foreground mt-0.5">
                  {formatEventDate(event.eventDate)}
                  {event.trackName && ` · ${event.trackName}`}
                </p>
              </div>
              <div className="flex items-center gap-3">
                <Badge className="bg-destructive/15 text-destructive">
                  In Progress
                </Badge>
                <ArrowRight className="h-4 w-4 text-muted-foreground group-hover:text-foreground transition-colors" />
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
