import { Suspense, useState } from 'react';
import { Outlet, NavLink } from 'react-router-dom';
import {
  Calendar,
  Trophy,
  Building2,
  MapPin,
  Sliders,
  LogOut,
  Menu,
  Flag,
  Radio,
  Volume2,
  Users,
  Wand2,
  HelpCircle,
  DatabaseBackup,
  CloudUpload,
  UserCog,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from '@/components/ui/sheet';
import { useHelp } from '@/context/HelpContext';
import { HelpSidebarButton } from '@/components/HelpSidebarButton';
import PageLoading from '@/components/PageLoading';
import { useRoles } from '@/hooks/useRoles';

interface NavEntry {
  to: string;
  label: string;
  Icon: LucideIcon;
  /** Shown to admins only: only an admin can change what it opens (#132). */
  adminOnly?: boolean;
}

const eventsGroup: NavEntry[] = [
  { to: '/admin/events', label: 'Events', Icon: Calendar },
  { to: '/admin/championships', label: 'Championships', Icon: Trophy },
];

const operationsGroup: NavEntry[] = [
  { to: '/admin/race-control', label: 'Race Control', Icon: Flag },
  { to: '/admin/competitors', label: 'Competitors', Icon: Users },
  { to: '/setup', label: 'Setup Wizard', Icon: Wand2 },
];

// Decoder settings, backups and results sent to RaceHub are admin-only, so these items are shown only to ADMIN users.
const adminOnlyOperations: NavEntry[] = [
  { to: '/admin/decoder', label: 'Decoder', Icon: Radio },
  { to: '/admin/backups', label: 'Backups', Icon: DatabaseBackup },
  { to: '/admin/results-exports', label: 'Results to RaceHub', Icon: CloudUpload },
  { to: '/admin/officials', label: 'Officials', Icon: UserCog },
];

const configGroup: NavEntry[] = [
  { to: '/admin/tracks', label: 'Tracks', Icon: MapPin, adminOnly: true },
  { to: '/admin/formats', label: 'Formats', Icon: Sliders, adminOnly: true },
  { to: '/admin/club', label: 'Club Profile', Icon: Building2, adminOnly: true },
  { to: '/admin/audio', label: 'Audio Settings', Icon: Volume2 },
];

/** The entries this official is allowed to open. */
function visibleTo(entries: NavEntry[], isAdmin: boolean): NavEntry[] {
  return entries.filter(entry => isAdmin || !entry.adminOnly);
}

function NavItem({
  to,
  label,
  Icon,
  onClick,
}: {
  to: string;
  label: string;
  Icon: React.ComponentType<{ className?: string }>;
  onClick?: () => void;
}) {
  return (
    <NavLink
      to={to}
      end={to === '/admin'}
      onClick={onClick}
      className={({ isActive }) =>
        `flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-colors ${
          isActive
            ? 'bg-primary/10 text-primary font-semibold border-l-[3px] border-primary pl-[calc(0.75rem-3px)]'
            : 'text-muted-foreground hover:text-foreground hover:bg-muted'
        }`
      }
    >
      <Icon className="h-4 w-4 shrink-0" />
      {label}
    </NavLink>
  );
}

// Used by both the desktop sidebar and the mobile sheet
function SidebarContent({ onNavClick }: { onNavClick?: () => void }) {
  const { user, logout } = useAuth();
  const { isAdmin } = useRoles();

  return (
    <div className="flex flex-col h-full">
      <div className="px-4 py-5">
        <span className="font-semibold text-base">RC Timing — Admin</span>
      </div>
      <Separator />

      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-1">
        <p className="px-3 mb-1 text-xs font-medium text-muted-foreground uppercase tracking-wide">
          Events &amp; Competitions
        </p>
        {visibleTo(eventsGroup, isAdmin).map(({ to, label, Icon }) => (
          <NavItem key={to} to={to} label={label} Icon={Icon} onClick={onNavClick} />
        ))}

        <div className="pt-4">
          <Separator />
        </div>

        <p className="px-3 pt-4 mb-1 text-xs font-medium text-muted-foreground uppercase tracking-wide">
          Operations
        </p>
        {visibleTo(operationsGroup, isAdmin).map(({ to, label, Icon }) => (
          <NavItem key={to} to={to} label={label} Icon={Icon} onClick={onNavClick} />
        ))}
        {isAdmin &&
          adminOnlyOperations.map(({ to, label, Icon }) => (
            <NavItem key={to} to={to} label={label} Icon={Icon} onClick={onNavClick} />
          ))}

        <div className="pt-4">
          <Separator />
        </div>

        <p className="px-3 pt-4 mb-1 text-xs font-medium text-muted-foreground uppercase tracking-wide">
          Configuration
        </p>
        {visibleTo(configGroup, isAdmin).map(({ to, label, Icon }) => (
          <NavItem key={to} to={to} label={label} Icon={Icon} onClick={onNavClick} />
        ))}
      </nav>

      <Separator />

      <div className="px-3 pt-3">
        <HelpSidebarButton onOpen={onNavClick} />
      </div>

      <div className="px-4 py-4 flex items-center justify-between">
        <div className="min-w-0">
          <p className="text-sm font-medium truncate">
            {user ? `${user.firstName} ${user.lastName}` : 'Admin'}
          </p>
          <p className="text-xs text-muted-foreground truncate">{user?.email}</p>
        </div>
        <Button
          variant="ghost"
          size="icon-sm"
          onClick={logout}
          aria-label="Log out"
          className="shrink-0"
        >
          <LogOut className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}

export default function AdminPanelLayout() {
  const [sheetOpen, setSheetOpen] = useState(false);
  const { helpContent, isOpen, setIsOpen } = useHelp();
  const { isAdmin } = useRoles();

  return (
    <div className="min-h-screen bg-background">
      {/* Desktop: fixed left sidebar */}
      <aside className="hidden md:flex fixed inset-y-0 left-0 w-60 border-r bg-card flex-col z-10">
        <SidebarContent />
      </aside>

      {/* Mobile: top bar with hamburger */}
      <header className="md:hidden flex items-center border-b h-14 px-4 sticky top-0 bg-background z-10">
        <Button
          variant="ghost"
          size="icon-sm"
          onClick={() => setSheetOpen(true)}
          aria-label="Open navigation"
        >
          <Menu className="h-5 w-5" />
        </Button>
        <span className="ml-3 font-semibold text-sm">RC Timing — Admin</span>
        {helpContent && (
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label="Open help"
            title="Open help"
            onClick={() => setIsOpen(true)}
            className="ml-auto"
          >
            <HelpCircle className="h-4 w-4" />
          </Button>
        )}
      </header>

      {/* Mobile: Sheet drawer */}
      <Sheet open={sheetOpen} onOpenChange={setSheetOpen}>
        <SheetContent side="left" showCloseButton className="w-72 p-0">
          <SheetHeader className="sr-only">
            <SheetTitle>Navigation</SheetTitle>
          </SheetHeader>
          <SidebarContent onNavClick={() => setSheetOpen(false)} />
        </SheetContent>
      </Sheet>

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

      <main className="md:pl-60">
        <div className="px-6 pt-6 pb-10 min-h-screen">
          <Suspense fallback={<PageLoading />}>
            <Outlet />
          </Suspense>
        </div>
      </main>

      <nav className="fixed bottom-0 inset-x-0 flex md:hidden border-t bg-background z-10 h-14">
        {visibleTo([...eventsGroup, ...operationsGroup, ...configGroup], isAdmin).slice(0, 5).map(({ to, label, Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={false}
            aria-label={label}
            className={({ isActive }) =>
              `flex-1 flex flex-col items-center justify-center py-2 text-xs gap-1 ${
                isActive ? 'text-primary' : 'text-muted-foreground'
              }`
            }
          >
            {({ isActive }) => (
              <>
                <Icon className="h-5 w-5" aria-hidden="true" />
                <span aria-current={isActive ? 'page' : undefined}>{label}</span>
              </>
            )}
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
