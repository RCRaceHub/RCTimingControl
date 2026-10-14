import { useState } from 'react';
import { Navigate, Link } from 'react-router-dom';
import { useHelp, useHelpContent } from '@/context/HelpContext';
import { HelpSidebarButton } from '@/components/HelpSidebarButton';
import { SetupWizardHelp } from '@/help/SetupWizardHelp';
import { Circle, CircleCheck, CircleDot, Loader2, Menu } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { useSetupStatus, useSetupProgress } from '@/hooks/setup/useSetupProgress';
import { useAuth } from '@/hooks/useAuth';
import { useRoles } from '@/hooks/useRoles';
import AdminBootstrapGate from './AdminBootstrapGate';
import ClubProfileStep from './steps/ClubProfileStep';
import TrackStep from './steps/TrackStep';
import FormatStep from './steps/FormatStep';
import StaffStep from './steps/StaffStep';
import DecoderConfigStep from './steps/DecoderConfigStep';
import SetupCompletePage from './SetupCompletePage';

type StepState = 'complete' | 'current' | 'incomplete';

function StepItem({
  number,
  label,
  state,
  onClick,
}: {
  number: number;
  label: string;
  state: StepState;
  onClick?: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-current={state === 'current' ? 'step' : undefined}
      disabled={!onClick}
      className={
        state === 'current'
          ? 'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-semibold text-primary border-l-[3px] border-primary pl-[calc(0.75rem-3px)] w-full text-left'
          : state === 'complete'
            ? 'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium text-foreground w-full text-left'
            : 'flex items-center gap-3 px-3 py-2 rounded-lg text-sm text-muted-foreground w-full text-left'
      }
    >
      {/* Done: a solid green disc with the tick knocked out in the background colour */}
      {state === 'complete' ? (
        <CircleCheck
          className="h-4 w-4 shrink-0 fill-[var(--flag-green)] text-background"
          aria-hidden="true"
        />
      ) : state === 'current' ? (
        <CircleDot className="h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
      ) : (
        <Circle className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
      )}
      <span className="text-xs text-muted-foreground mr-1">{number}.</span>
      {label}
    </button>
  );
}

const STEPS: { key: 'club' | 'track' | 'format' | 'staff' | 'decoder'; label: string }[] = [
  { key: 'club', label: 'Club Profile' },
  { key: 'track', label: 'Track' },
  { key: 'format', label: 'Race Format' },
  { key: 'staff', label: 'Staff Account' },
  { key: 'decoder', label: 'Decoder Config' },
];

function SidebarContent({
  onNavClick,
  currentStep,
  clickable,
  onStepClick,
}: {
  onNavClick?: () => void;
  currentStep: number;
  clickable: boolean;
  onStepClick?: (step: number) => void;
}) {
  const { data: progress } = useSetupProgress();

  function getStepState(key: string, index: number): StepState {
    if (!progress) return index === 0 ? 'current' : 'incomplete';
    const isComplete = progress[key as keyof typeof progress] as boolean;
    if (isComplete) return 'complete';
    // Current = matches the active step tracked by SetupLayout
    if (index + 1 === currentStep) return 'current';
    return 'incomplete';
  }

  return (
    <div className="flex flex-col h-full">
      <div className="px-4 py-5">
        <span className="font-semibold text-base">RC Timing — Setup</span>
      </div>
      <Separator />

      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-1" onClick={onNavClick}>
        {STEPS.map(({ key, label }, index) => (
          <StepItem
            key={key}
            number={index + 1}
            label={label}
            state={getStepState(key, index)}
            onClick={clickable && onStepClick ? () => onStepClick(index + 1) : undefined}
          />
        ))}
      </nav>

      <Separator />

      <div className="px-3 pt-3">
        <HelpSidebarButton onOpen={onNavClick} />
      </div>

      {/* Skip wizard link — only shown in re-entry mode (setup already complete) */}
      {clickable && (
        <div className="px-4 py-4">
          <Button variant="ghost" size="sm" className="text-sm text-muted-foreground w-full" asChild>
            <Link to="/admin">Skip wizard</Link>
          </Button>
        </div>
      )}
    </div>
  );
}

export default function SetupLayout() {
  const [sheetOpen, setSheetOpen] = useState(false);
  const [currentStep, setCurrentStep] = useState<number>(1);
  const { data: statusData, isLoading: statusLoading } = useSetupStatus();
  const { user } = useAuth();
  const { isAdmin } = useRoles();
  const { helpContent, isOpen, setIsOpen } = useHelp();

  useHelpContent(SetupWizardHelp);

  // Only fetch progress once authenticated — /setup/progress requires auth and a 401 here
  // would trigger the refresh interceptor loop before bootstrap completes.
  const { data: progress } = useSetupProgress({ enabled: !!user });

  // Move to the first incomplete step whenever progress loads or changes (adjusting state while rendering
  // rather than in an effect)
  const [seenProgress, setSeenProgress] = useState<typeof progress>(undefined);
  if (progress !== seenProgress) {
    setSeenProgress(progress);
    if (progress) {
      if (!progress.club) setCurrentStep(1);
      else if (!progress.track) setCurrentStep(2);
      else if (!progress.format) setCurrentStep(3);
      else if (!progress.staff) setCurrentStep(4);
      else if (!progress.decoder) setCurrentStep(5);
      else setCurrentStep(6); // all complete: show the summary
    }
  }

  if (statusLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  if (!user) {
    // No admin account yet — show the bootstrap form
    if (statusData?.bootstrapped === false) {
      return <AdminBootstrapGate />;
    }
    // Admin exists but this browser has no session — go to login, preserving return path
    return <Navigate to="/login?from=/setup" replace />;
  }

  // Setup complete but user is not ADMIN → redirect to login
  if (statusData?.setupComplete === true && !isAdmin) {
    return <Navigate to="/login" replace />;
  }

  const handleNext = () => setCurrentStep((s) => Math.min(s + 1, 6));
  const handleBack = () => setCurrentStep((s) => Math.max(s - 1, 1));

  // In re-entry mode (setup already complete), sidebar steps are clickable
  const clickable = statusData?.setupComplete === true;

  let stepContent: React.ReactNode;
  switch (currentStep) {
    case 1:
      stepContent = <ClubProfileStep onNext={handleNext} />;
      break;
    case 2:
      stepContent = <TrackStep onNext={handleNext} onBack={handleBack} />;
      break;
    case 3:
      stepContent = <FormatStep onNext={handleNext} onBack={handleBack} />;
      break;
    case 4:
      stepContent = <StaffStep onNext={handleNext} onBack={handleBack} />;
      break;
    case 5:
      stepContent = <DecoderConfigStep onNext={handleNext} onBack={handleBack} />;
      break;
    default:
      stepContent = <SetupCompletePage />;
  }

  return (
    <div className="min-h-screen bg-background">
      {/* Desktop: fixed left sidebar */}
      <aside className="hidden md:flex fixed inset-y-0 left-0 w-60 border-r bg-card flex-col z-10">
        <SidebarContent
          currentStep={currentStep}
          clickable={clickable}
          onStepClick={setCurrentStep}
        />
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
        <span className="ml-3 font-semibold text-sm">RC Timing — Setup</span>
      </header>

      {/* Mobile: Sheet drawer */}
      <Sheet open={sheetOpen} onOpenChange={setSheetOpen}>
        <SheetContent side="left" showCloseButton className="w-72 p-0">
          <SheetHeader className="sr-only">
            <SheetTitle>Setup Steps</SheetTitle>
          </SheetHeader>
          <SidebarContent
            onNavClick={() => setSheetOpen(false)}
            currentStep={currentStep}
            clickable={clickable}
            onStepClick={(step) => {
              setCurrentStep(step);
              setSheetOpen(false);
            }}
          />
        </SheetContent>
      </Sheet>

      {/* Help Sheet: the wizard registered its help but nothing showed it */}
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
          {stepContent}
        </div>
      </main>
    </div>
  );
}
