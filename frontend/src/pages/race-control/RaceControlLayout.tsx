import { Suspense } from 'react';
import { Outlet, Link, useParams, useMatches } from 'react-router-dom';
import { Flag, Shield, LogOut, ChevronLeft, Dumbbell, HelpCircle, ScanLine } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from '@/components/ui/sheet';
import { useHelp } from '@/context/HelpContext';
import { DecoderStatusBar } from './panels/DecoderStatusBar';
import { LiveFeedStatus } from './panels/LiveFeedStatus';
import { RaceControlErrorBoundary } from '@/components/RaceControlErrorBoundary';
import PageLoading from '@/components/PageLoading';

export default function RaceControlLayout() {
  const { eventId } = useParams<{ eventId: string }>();
  const { user, logout } = useAuth();
  const { helpContent, isOpen, setIsOpen } = useHelp();
  const matches = useMatches();

  const base = `/race-control/event/${eventId}`;
  const isPractice = matches.some((m) => m.pathname.includes('/practice'));
  const isReferee = matches.some((m) => m.pathname.endsWith('/referee'));
  const isCheckIn = matches.some((m) => m.pathname.endsWith('/check-in'));

  function navClass(active: boolean) {
    return `flex items-center gap-1.5 px-3 py-1.5 rounded text-sm transition-colors ${
      active
        ? 'bg-primary text-primary-foreground font-medium'
        : 'text-muted-foreground hover:text-foreground hover:bg-muted'
    }`;
  }

  return (
    <div className="flex flex-col h-screen overflow-hidden bg-background">
      <header className="flex items-center h-12 px-4 border-b bg-card shrink-0 gap-4">
        <Link
          to="/admin/race-control"
          className="flex items-center gap-1 text-muted-foreground hover:text-foreground transition-colors"
          aria-label="Back to event select"
        >
          <ChevronLeft className="h-4 w-4" />
          <span className="text-sm font-semibold">RC Timing</span>
        </Link>

        <nav className="flex items-center gap-1 ml-4">
          <Link to={base} className={navClass(!isReferee && !isPractice && !isCheckIn)}>
            <Flag className="h-3.5 w-3.5" />
            Cockpit
          </Link>
          <Link to={`${base}/practice`} className={navClass(isPractice)}>
            <Dumbbell className="h-3.5 w-3.5" />
            Practice
          </Link>
          <Link to={`${base}/check-in`} className={navClass(isCheckIn)}>
            <ScanLine className="h-3.5 w-3.5" />
            Check-in
          </Link>
          <Link to={`${base}/referee`} className={navClass(isReferee)}>
            <Shield className="h-3.5 w-3.5" />
            Referee
          </Link>
        </nav>

        <div className="ml-auto flex items-center gap-3">
          <span className="text-xs text-muted-foreground hidden sm:block">
            {user ? `${user.firstName} ${user.lastName}` : ''}
          </span>
          {helpContent && (
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label="Open help"
              title="Open help"
              onClick={() => setIsOpen(true)}
            >
              <HelpCircle className="h-4 w-4" />
            </Button>
          )}
          <Button variant="ghost" size="icon-sm" onClick={logout} aria-label="Log out">
            <LogOut className="h-4 w-4" />
          </Button>
        </div>
      </header>

      <Sheet open={isOpen} onOpenChange={setIsOpen}>
        <SheetContent side="right" className="w-96" showCloseButton>
          <SheetHeader>
            <SheetTitle>Help</SheetTitle>
            <SheetDescription>Page guide</SheetDescription>
          </SheetHeader>
          <div className="overflow-y-auto flex-1 px-6 pb-6">
            {helpContent}
          </div>
        </SheetContent>
      </Sheet>

      <Separator />

      <DecoderStatusBar>
        {eventId && <LiveFeedStatus eventId={Number(eventId)} />}
      </DecoderStatusBar>

      {/* Page content fills remaining height */}
      <div className="flex-1 overflow-hidden">
        <RaceControlErrorBoundary>
          <Suspense fallback={<PageLoading />}>
            <Outlet />
          </Suspense>
        </RaceControlErrorBoundary>
      </div>
    </div>
  );
}

