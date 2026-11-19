import { DecoderSettingsForm } from '@/components/decoder/DecoderSettingsForm';
import { SplitDecodersCard } from '@/components/decoder/SplitDecodersCard';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useHelpContent } from '@/context/HelpContext';
import { DecoderHelp } from '@/help/DecoderHelp';

export default function DecoderSettingsPage() {
  useHelpContent(DecoderHelp);

  return (
    <div>
      <h1 className="text-2xl font-semibold mb-2">Decoder</h1>
      <p className="text-sm text-muted-foreground mb-6">
        The AMB decoder that RCTC reads live laps from. RCTC connects to it directly over the venue
        network.
      </p>

      <Card className="max-w-xl">
        <CardHeader>
          <CardTitle className="text-base">Connection</CardTitle>
        </CardHeader>
        <CardContent>
          <DecoderSettingsForm saveLabel="Save" />
        </CardContent>
      </Card>

      <SplitDecodersCard />
    </div>
  );
}
