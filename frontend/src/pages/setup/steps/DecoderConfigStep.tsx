import { Link } from 'react-router-dom';
import { DecoderSettingsForm } from '@/components/decoder/DecoderSettingsForm';

interface Props {
  onNext: () => void;
  onBack?: () => void;
}

export default function DecoderConfigStep({ onNext, onBack }: Props) {
  return (
    <div>
      <h1 className="text-2xl font-semibold mb-2">Decoder Config</h1>
      <p className="text-sm text-muted-foreground mb-6">
        Configure your AMB decoder connection so RCTC can read live lap data.
      </p>

      <DecoderSettingsForm
        onSaved={onNext}
        onBack={onBack}
        onSkip={onNext}
        saveLabel="Save and Continue"
      />

      <Link to="/admin/decoder" className="text-sm text-muted-foreground underline mt-4 inline-block">
        Manage more in Admin →
      </Link>
    </div>
  );
}
