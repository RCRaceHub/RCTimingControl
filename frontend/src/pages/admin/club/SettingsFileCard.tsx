import { useRef, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import axios from 'axios';

import { Button } from '@/components/ui/button';
import { useExportClubSettings, useImportClubSettings } from '@/hooks/admin/useAdminClub';
import type { SettingsSectionDto } from '@/lib/adminApi';

/** The server's explanation of a refused file, such as the version it reads, when it gave one. */
function problemDetail(error: unknown): string | undefined {
  return axios.isAxiosError(error) ? error.response?.data?.detail : undefined;
}

/**
 * The club settings file (#510): download this install's settings as one small file, and read a file from another
 * install back in. Choosing a file shows what it holds and which parts would change anything; nothing is changed
 * until the admin confirms. The file has no race history, officials, sign-in key, logo or RaceHub address.
 */
export default function SettingsFileCard() {
  const exportSettings = useExportClubSettings();
  const importSettings = useImportClubSettings();
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<unknown>(null);
  const [sections, setSections] = useState<SettingsSectionDto[] | null>(null);

  async function choose(chosen: File | undefined) {
    setSections(null);
    setFile(null);
    if (!chosen) return;
    let parsed: unknown;
    try {
      parsed = JSON.parse(await chosen.text());
    } catch {
      toast.error('That is not a settings file.');
      return;
    }
    importSettings.mutate({ file: parsed, apply: false }, {
      onSuccess: (preview) => {
        setFile(parsed);
        setSections(preview);
      },
      onError: (error) => toast.error(problemDetail(error) ?? 'That is not a settings file this version of RC Timing can read.'),
    });
  }

  function apply() {
    importSettings.mutate({ file, apply: true }, {
      onSuccess: () => {
        toast.success('Settings imported');
        setSections(null);
        setFile(null);
        if (input.current) input.current.value = '';
      },
      onError: (error) => toast.error(
        problemDetail(error) ?? 'The settings could not be imported, so nothing was changed.'),
    });
  }

  const changing = sections?.filter(s => s.changes) ?? [];

  return (
    <div className="space-y-6 text-sm">
      <div className="space-y-2">
        <h2 className="font-medium">Save these settings</h2>
        <p className="text-muted-foreground">
          One small file with the club's details, announcer settings, live timing thresholds, decoder address, where
          results are published, privacy notice and governing bodies. It leaves out race history, officials, the sign-in
          key, the logo and the RaceHub address, so it is safe to keep with your other club papers. Back up the whole
          data folder as well for everything else.
        </p>
        <Button variant="outline" disabled={exportSettings.isPending} onClick={() => exportSettings.mutate()}>
          Download the settings file
        </Button>
      </div>

      <div className="space-y-2 rounded-lg border p-4">
        <h2 className="font-medium">Use a settings file</h2>
        <p className="text-muted-foreground">
          Choose a file saved from another install, or from this one earlier. You see what it holds before anything is
          changed.
        </p>
        <input
          ref={input}
          type="file"
          accept="application/json,.json"
          aria-label="Settings file"
          onChange={(e) => void choose(e.target.files?.[0])}
        />
        {importSettings.isPending && <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />}
        {sections && (
          <div className="space-y-2">
            <ul aria-label="In the file" className="space-y-1">
              {sections.map(s => (
                <li key={s.name}>
                  <strong>{s.name}</strong>{s.summary ? `: ${s.summary}` : ''}
                  {' '}<span className="text-muted-foreground">({s.changes ? 'will change' : 'same as now'})</span>
                </li>
              ))}
            </ul>
            {changing.length === 0 ? (
              <p className="text-muted-foreground">Everything in this file is already how this install is set.</p>
            ) : (
              <Button disabled={importSettings.isPending} onClick={apply}>
                Import {changing.length === 1 ? '1 change' : `${changing.length} changes`}
              </Button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
