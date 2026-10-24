import { useState } from 'react';
import { ExternalLink, Play, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { sendCueTo } from '@/lib/audioOutput';
import {
  LOCAL_OUTPUT_ID,
  SOUND_ROUTES,
  outputIdFor,
  outputsFor,
  saveRouting,
  useAudioRouting,
  type AudioRouting,
  type OutputPan,
  type SoundRoute,
} from '@/lib/audioRouting';

const PANS: { value: OutputPan; label: string }[] = [
  { value: 'both', label: 'Both speakers' },
  { value: 'left', label: 'Left only' },
  { value: 'right', label: 'Right only' },
];

/**
 * Which outputs play each sound (#478): a grid with a row for every sound and a column for every output. An
 * output is a browser tab on this machine, opened from the link under its name, that plays on the sound card
 * and side chosen there. Race control's own tab is always an output. The grid is saved in this browser, since a
 * sound card belongs to the machine and the output tabs read it from here.
 */
export function AudioRoutingCard() {
  const routing = useAudioRouting();
  const [newName, setNewName] = useState('');

  const update = (next: AudioRouting) => saveRouting(next);

  const toggle = (sound: SoundRoute, outputId: string, on: boolean) => {
    const current = outputsFor(routing, sound);
    const next = on ? [...current, outputId] : current.filter((id) => id !== outputId);
    update({ ...routing, routes: { ...routing.routes, [sound]: next } });
  };

  const addOutput = () => {
    const name = newName.trim();
    if (!name) return;
    const id = outputIdFor(name, routing.outputs.map((o) => o.id));
    update({ ...routing, outputs: [...routing.outputs, { id, name, pan: 'both' }] });
    setNewName('');
  };

  const removeOutput = (id: string) => {
    const routes = Object.fromEntries(
      Object.entries(routing.routes).map(([sound, ids]) => [sound, ids.filter((out) => out !== id)]),
    );
    update({ outputs: routing.outputs.filter((o) => o.id !== id), routes });
  };

  const setPan = (id: string, pan: OutputPan) =>
    update({ ...routing, outputs: routing.outputs.map((o) => (o.id === id ? { ...o, pan } : o)) });

  const rename = (id: string, name: string) =>
    update({ ...routing, outputs: routing.outputs.map((o) => (o.id === id ? { ...o, name: name.trim() || o.name } : o)) });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Where each sound plays</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-muted-foreground">
          Add an output for each place you want sound, such as the rostrum and the pits, then tick the outputs that
          play each sound. A sound can go to more than one. Open each output&apos;s page in Chrome or Edge on this
          laptop, choose its sound card there, and leave the tab open. This is saved in this browser.
        </p>

        <div className="space-y-3">
          {routing.outputs.map((output) => (
            <div key={output.id} className="flex flex-wrap items-center gap-2">
              <Input
                aria-label={`Name of ${output.name}`}
                key={output.name}
                defaultValue={output.name}
                maxLength={40}
                disabled={output.id === LOCAL_OUTPUT_ID}
                onBlur={(e) => rename(output.id, e.target.value)}
                className="w-48 h-8"
              />
              <select
                aria-label={`Speakers for ${output.name}`}
                value={output.pan}
                onChange={(e) => setPan(output.id, e.target.value as OutputPan)}
                className="h-8 rounded-md border bg-background px-2 text-sm"
              >
                {PANS.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
              </select>
              {output.id !== LOCAL_OUTPUT_ID && (
                <a
                  href={`/audio-output/${output.id}`}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1 text-sm underline"
                >
                  Open output page <ExternalLink className="h-3 w-3" aria-hidden="true" />
                </a>
              )}
              <Button
                variant="outline"
                size="sm"
                onClick={() => sendCueTo(output.id, 0.8, { kind: 'speech', text: `This is ${output.name}.` })}
              >
                <Play className="mr-1 h-3 w-3" aria-hidden="true" /> Test
              </Button>
              {output.id !== LOCAL_OUTPUT_ID && (
                <Button
                  variant="ghost"
                  size="sm"
                  aria-label={`Remove ${output.name}`}
                  onClick={() => removeOutput(output.id)}
                >
                  <Trash2 className="h-4 w-4" aria-hidden="true" />
                </Button>
              )}
            </div>
          ))}
          <div className="flex items-center gap-2">
            <Input
              aria-label="New output name"
              placeholder="New output, for example Rostrum"
              value={newName}
              maxLength={40}
              onChange={(e) => setNewName(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') addOutput(); }}
              className="w-64 h-8"
            />
            <Button size="sm" variant="outline" onClick={addOutput} disabled={!newName.trim()}>Add output</Button>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left">
                <th className="py-2 pr-4 font-medium">Sound</th>
                {routing.outputs.map((o) => (
                  <th key={o.id} className="py-2 px-2 font-medium text-center">{o.name}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {SOUND_ROUTES.map(({ id, label }) => {
                const picked = outputsFor(routing, id);
                return (
                  <tr key={id} className="border-t">
                    <td className="py-2 pr-4">{label}</td>
                    {routing.outputs.map((o) => (
                      <td key={o.id} className="py-2 px-2 text-center">
                        <Checkbox
                          aria-label={`${label} on ${o.name}`}
                          checked={picked.includes(o.id)}
                          onCheckedChange={(on) => toggle(id, o.id, on === true)}
                        />
                      </td>
                    ))}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        <p className="text-xs text-muted-foreground">
          Spoken lines on an output page use the club&apos;s Piper voice, because the browser&apos;s own voice
          always plays on the default sound card. If Piper is not running, that line plays on the default card.
        </p>
      </CardContent>
    </Card>
  );
}
