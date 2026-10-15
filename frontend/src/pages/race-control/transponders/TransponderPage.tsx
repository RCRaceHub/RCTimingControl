// Race control's transponder page (L11): swap a transponder on the day.
import { useParams } from 'react-router-dom';
import { Card } from '@/components/ui/card';
import { useHelpContent } from '@/context/HelpContext';
import { TransponderHelp } from '@/help/TransponderHelp';
import TransponderSwap from './TransponderSwap';

export default function TransponderPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const id = Number(eventId);

  useHelpContent(TransponderHelp);

  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-xl p-6">
        <Card className="p-6">
          <h1 className="mb-4 text-lg font-semibold">Swap a transponder</h1>
          <TransponderSwap eventId={id} />
        </Card>
      </div>
    </div>
  );
}
