import ClassesPage from '@/pages/admin/classes/ClassesPage';
import FormatsPage from '@/pages/admin/formats/FormatsPage';
import ScoringSystemsPage from '@/pages/admin/scoring/ScoringSystemsPage';
import TracksPage from '@/pages/admin/tracks/TracksPage';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useTabParam } from '@/hooks/useTabParam';

const TABS = ['classes', 'formats', 'tracks', 'scoring'] as const;

/**
 * The club's racing reference data on one page: classes, race formats, tracks and scoring systems. They used to
 * be four pages in the menu. The tab is kept in the address (`?tab=`), and the old addresses redirect here.
 * Admin only, like each of them was.
 */
export default function RacingSetupPage() {
  const [tab, setTab] = useTabParam(TABS, 'classes');
  return (
    <div className="space-y-6">
      <Tabs value={tab} onValueChange={setTab} className="space-y-6">
        <TabsList>
          <TabsTrigger value="classes">Classes</TabsTrigger>
          <TabsTrigger value="formats">Formats</TabsTrigger>
          <TabsTrigger value="tracks">Tracks</TabsTrigger>
          <TabsTrigger value="scoring">Scoring systems</TabsTrigger>
        </TabsList>
        <TabsContent value="classes"><ClassesPage /></TabsContent>
        <TabsContent value="formats"><FormatsPage /></TabsContent>
        <TabsContent value="tracks"><TracksPage /></TabsContent>
        <TabsContent value="scoring"><ScoringSystemsPage /></TabsContent>
      </Tabs>
    </div>
  );
}
