import BoardViewsPage from '@/pages/admin/board-views/BoardViewsPage';
import OverlayDesignerPage from '@/pages/admin/board-views/OverlayDesignerPage';
import ScreensPage from '@/pages/admin/screens/ScreensPage';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useTabParam } from '@/hooks/useTabParam';

const TABS = ['views', 'screens', 'overlay'] as const;

/**
 * Everything that sets what the club's displays show, on one page: custom views for pit and TV boards, the
 * Raspberry Pi screens with their messages and banners, and the streaming overlay designer. They used to be three
 * menu items. The tab is kept in the address (`?tab=`), and the old addresses redirect here. Open to an admin or
 * a race director, like each of them was.
 */
export default function DisplaysPage() {
  const [tab, setTab] = useTabParam(TABS, 'views');
  return (
    <Tabs value={tab} onValueChange={setTab} className="space-y-6">
      <TabsList>
        <TabsTrigger value="views">Custom views</TabsTrigger>
        <TabsTrigger value="screens">Screens</TabsTrigger>
        <TabsTrigger value="overlay">Overlay designer</TabsTrigger>
      </TabsList>
      <TabsContent value="views"><BoardViewsPage /></TabsContent>
      <TabsContent value="screens"><ScreensPage /></TabsContent>
      <TabsContent value="overlay"><OverlayDesignerPage /></TabsContent>
    </Tabs>
  );
}
