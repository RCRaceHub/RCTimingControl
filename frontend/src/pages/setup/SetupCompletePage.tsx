import { useNavigate, Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { useSetupProgress } from '@/hooks/setup/useSetupProgress';
import type { SetupProgressDto } from '@/lib/setupApi';

// Staff accounts have no admin page yet, so they are edited from the wizard's Staff step
type Item = { key: keyof SetupProgressDto; label: string; editHref?: string };

const items: Item[] = [
  { key: 'club',    label: 'Club Profile',   editHref: '/admin/club' },
  { key: 'track',   label: 'Track',          editHref: '/admin/racing-setup?tab=tracks' },
  { key: 'format',  label: 'Race Format',    editHref: '/admin/racing-setup?tab=formats' },
  { key: 'staff',   label: 'Staff Account' },
  { key: 'decoder', label: 'Decoder Config', editHref: '/admin/decoder' },
];

export default function SetupCompletePage() {
  const navigate = useNavigate();
  const { data: progress } = useSetupProgress();

  return (
    <div>
      <h1 className="text-2xl font-semibold mb-2">Setup Complete</h1>
      <p className="text-sm text-muted-foreground mb-6">Your club is ready to run a meeting.</p>

      <div className="space-y-4 mb-6">
        {items.map(it => (
          <Card key={it.key}>
            <CardContent className="flex items-center justify-between p-4">
              <div className="flex items-center gap-3">
                <span className="font-medium">{it.label}</span>
                {progress?.[it.key] ? (
                  <Badge variant="outline" className="border-[var(--flag-green)] text-[var(--flag-green)]">
                    Configured
                  </Badge>
                ) : (
                  <Badge variant="outline" className="text-muted-foreground">
                    Skipped — configure later
                  </Badge>
                )}
              </div>
              {it.editHref && (
                <Link to={it.editHref} className="text-sm underline text-foreground">
                  Edit
                </Link>
              )}
            </CardContent>
          </Card>
        ))}
      </div>

      <Button onClick={() => navigate('/admin')}>Go to Admin Panel</Button>
    </div>
  );
}
