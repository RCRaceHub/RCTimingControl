import { lazy, Suspense } from 'react';
import { createBrowserRouter, RouterProvider, Navigate, Outlet } from 'react-router-dom';
import { QueryProvider } from '@/providers/QueryProvider';
import { AuthProvider } from '@/providers/AuthProvider';
import ProtectedRoute from '@/components/ProtectedRoute';
import { RaceControlErrorBoundary } from '@/components/RaceControlErrorBoundary';
import SetupGuard from '@/pages/setup/SetupGuard';
import LoginPage from '@/pages/auth/LoginPage';
import NotFoundPage from '@/pages/NotFoundPage';
import UnauthorizedPage from '@/pages/UnauthorizedPage';
import { Toaster } from '@/components/ui/sonner';
import { HelpProvider } from '@/context/HelpContext';
import PageLoading from '@/components/PageLoading';
// The overlay sits over a live stream, so it loads with the app: a loading spinner would show on air
import OverlayBoard from '@/pages/boards/OverlayBoard';

// Every page but sign-in and the error pages loads on demand, so a board or the overlay doesn't download
// the admin panel and the print guides. The layouts wrap their outlets in Suspense to keep their chrome.
const SetupLayout = lazy(() => import('@/pages/setup/SetupLayout'));
const PrivacyPage = lazy(() => import('@/pages/PrivacyPage'));
const EventSchedulePage = lazy(() => import('@/pages/events/EventSchedulePage'));
const AdminPanelLayout = lazy(() => import('@/pages/admin/AdminPanelLayout'));
const EventListPage = lazy(() => import('@/pages/admin/events/EventListPage'));
const EventDetailPage = lazy(() => import('@/pages/admin/events/EventDetailPage'));
const ChampionshipListPage = lazy(() => import('@/pages/admin/championships/ChampionshipListPage'));
const ChampionshipDetailPage = lazy(() => import('@/pages/admin/championships/ChampionshipDetailPage'));
const ClubProfilePage = lazy(() => import('@/pages/admin/club/ClubProfilePage'));
const AdminAudioSettingsPage = lazy(() => import('@/pages/admin/club/AdminAudioSettingsPage'));
const OfficialsPage = lazy(() => import('@/pages/admin/officials/OfficialsPage'));
const TracksPage = lazy(() => import('@/pages/admin/tracks/TracksPage'));
const FormatsPage = lazy(() => import('@/pages/admin/formats/FormatsPage'));
const RaceControlSelectPage = lazy(() => import('@/pages/admin/race-control/RaceControlSelectPage'));
const DecoderSettingsPage = lazy(() => import('@/pages/admin/decoder/DecoderSettingsPage'));
const BackupsPage = lazy(() => import('@/pages/admin/backups/BackupsPage'));
const ResultsExportsPage = lazy(() => import('@/pages/admin/results-exports/ResultsExportsPage'));
const CompetitorsPage = lazy(() => import('@/pages/admin/competitors/CompetitorsPage'));
const CheckInPage = lazy(() => import('@/pages/race-control/check-in/CheckInPage'));
const RaceControlLayout = lazy(() => import('@/pages/race-control/RaceControlLayout'));
const CockpitPage = lazy(() => import('@/pages/race-control/CockpitPage'));
const RefereePage = lazy(() => import('@/pages/race-control/RefereePage'));
const PrintResultsPage = lazy(() => import('@/pages/race-control/PrintResultsPage'));
const PracticeSessionPage = lazy(() =>
  import('@/pages/race-control/PracticeSessionPage').then(m => ({ default: m.PracticeSessionPage })),
);
const PracticeLandingPage = lazy(() =>
  import('@/pages/race-control/PracticeLandingPage').then(m => ({ default: m.PracticeLandingPage })),
);
const PracticeDayPage = lazy(() =>
  import('@/pages/race-control/PracticeDayPage').then(m => ({ default: m.PracticeDayPage })),
);
const PrintPracticeResultsPage = lazy(() => import('@/pages/race-control/PrintPracticeResultsPage'));
const PublicResultsPage = lazy(() => import('@/pages/results/PublicResultsPage'));
const PublicChampionshipPage = lazy(() => import('@/pages/championships/PublicChampionshipPage'));
const MeetingGuidePage = lazy(() => import('@/pages/print/MeetingGuidePage'));
const AdminGuidePage = lazy(() => import('@/pages/print/AdminGuidePage'));
const AboutPage = lazy(() => import('@/pages/AboutPage'));
const NowNextBoard = lazy(() => import('@/pages/boards/NowNextBoard'));
const ResultsBoard = lazy(() => import('@/pages/boards/ResultsBoard'));

function RootLayout() {
  return (
    <AuthProvider>
      <HelpProvider>
        <SetupGuard>
          <Suspense fallback={<PageLoading />}>
            <Outlet />
          </Suspense>
        </SetupGuard>
      </HelpProvider>
      <Toaster />
    </AuthProvider>
  );
}

const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      { path: '/', element: <Navigate to="/login" replace /> },
      { path: '/login', element: <LoginPage /> },
      {
        path: '/admin',
        element: (
          <ProtectedRoute roles={['ADMIN', 'RACE_DIRECTOR', 'REFEREE']}>
            <AdminPanelLayout />
          </ProtectedRoute>
        ),
        children: [
          { index: true, element: <Navigate to="/admin/events" replace /> },
          { path: 'events', element: <EventListPage /> },
          { path: 'events/:id', element: <EventDetailPage /> },
          // Only an admin can change these, so only an admin opens them (#132). Everyone can still read the data through the API and the public pages.
          // Championships stay open to every official: a referee records a DQ there. Only an admin can change the rest of a championship.
          { path: 'championships', element: <ChampionshipListPage /> },
          { path: 'championships/:id', element: <ChampionshipDetailPage /> },
          { path: 'club', element: <ProtectedRoute roles={['ADMIN']}><ClubProfilePage /></ProtectedRoute> },
          { path: 'tracks', element: <ProtectedRoute roles={['ADMIN']}><TracksPage /></ProtectedRoute> },
          { path: 'formats', element: <ProtectedRoute roles={['ADMIN']}><FormatsPage /></ProtectedRoute> },
          { path: 'race-control', element: <RaceControlSelectPage /> },
          { path: 'decoder', element: <ProtectedRoute roles={['ADMIN']}><DecoderSettingsPage /></ProtectedRoute> },
          { path: 'backups', element: <ProtectedRoute roles={['ADMIN']}><BackupsPage /></ProtectedRoute> },
          { path: 'results-exports', element: <ProtectedRoute roles={['ADMIN']}><ResultsExportsPage /></ProtectedRoute> },
          { path: 'officials', element: <ProtectedRoute roles={['ADMIN']}><OfficialsPage /></ProtectedRoute> },
          { path: 'audio', element: <AdminAudioSettingsPage /> },
          { path: 'competitors', element: <CompetitorsPage /> },
        ],
      },
      {
        path: '/race-control/event/:eventId',
        element: (
          <ProtectedRoute roles={['RACE_DIRECTOR', 'REFEREE', 'ADMIN']}>
            <RaceControlLayout />
          </ProtectedRoute>
        ),
        children: [
          { index: true, element: <CockpitPage /> },
          { path: 'practice', element: <PracticeLandingPage /> },
          { path: 'check-in', element: <CheckInPage /> },
          { path: 'referee', element: <RefereePage /> },
          { path: 'results/:raceId', element: <PrintResultsPage /> },
        ],
      },
      {
        path: '/race-control/practice/day',
        element: (
          <ProtectedRoute roles={['RACE_DIRECTOR', 'ADMIN']}>
            <RaceControlErrorBoundary>
              <PracticeDayPage />
            </RaceControlErrorBoundary>
          </ProtectedRoute>
        ),
      },
      {
        path: '/race-control/practice/:sessionId',
        element: (
          <ProtectedRoute roles={['RACE_DIRECTOR', 'ADMIN']}>
            <RaceControlErrorBoundary>
              <PracticeSessionPage />
            </RaceControlErrorBoundary>
          </ProtectedRoute>
        ),
      },
      {
        path: '/race-control/practice/:sessionId/print',
        element: (
          <ProtectedRoute roles={['RACE_DIRECTOR', 'ADMIN']}>
            <PrintPracticeResultsPage />
          </ProtectedRoute>
        ),
      },
      { path: '/setup', element: <SetupLayout /> },
      { path: '/events', element: <EventSchedulePage /> },
      { path: '/results/:raceId', element: <PublicResultsPage /> },
      { path: '/championships/:id', element: <PublicChampionshipPage /> },
      { path: '/print/meeting-guide', element: <MeetingGuidePage /> },
      { path: '/print/admin-guide', element: <AdminGuidePage /> },
      { path: '/about', element: <AboutPage /> },
      { path: '/privacy', element: <PrivacyPage /> },
      { path: '/boards/now-next', element: <NowNextBoard /> },
      { path: '/boards/results', element: <ResultsBoard /> },
      { path: '/boards/overlay', element: <OverlayBoard /> },
      { path: '/unauthorized', element: <UnauthorizedPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);

export default function App() {
  return (
    <QueryProvider>
      <RouterProvider router={router} />
    </QueryProvider>
  );
}
