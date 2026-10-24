import { useState } from 'react';
import { ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { PANEL_TYPES, type BoardPanel, type BoardPanelType, type BoardViewInput } from '@/lib/boardViewsApi';

/** A panel as the server stores it: only a live top panel has a number of places. */
function withType(panel: BoardPanel, type: BoardPanelType): BoardPanel {
  return { ...panel, type, top: type === 'LIVE_TOP' ? (panel.top ?? 10) : null };
}

/**
 * Builds or changes a saved board view: its address name, name, columns and an ordered list of panels, each with a
 * type, a width in columns and (for the live top table) a number of places. The server checks the layout again.
 */
export function BoardViewDialog({
  open,
  onOpenChange,
  title,
  initial,
  onSubmit,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  initial: BoardViewInput;
  onSubmit: (view: BoardViewInput) => Promise<void>;
}) {
  const [view, setView] = useState<BoardViewInput>(initial);
  const [saving, setSaving] = useState(false);

  function setPanel(index: number, panel: BoardPanel) {
    setView(v => ({ ...v, panels: v.panels.map((p, i) => (i === index ? panel : p)) }));
  }

  function move(index: number, by: -1 | 1) {
    setView(v => {
      const panels = [...v.panels];
      const target = index + by;
      if (target < 0 || target >= panels.length) return v;
      [panels[index], panels[target]] = [panels[target], panels[index]];
      return { ...v, panels };
    });
  }

  function setColumns(columns: number) {
    // A panel can't be wider than the view
    setView(v => ({ ...v, columns, panels: v.panels.map(p => ({ ...p, span: Math.min(p.span, columns) })) }));
  }

  async function submit() {
    setSaving(true);
    try {
      await onSubmit(view);
    } finally {
      setSaving(false);
    }
  }

  const canSave = view.slug.trim() !== '' && view.name.trim() !== '' && view.panels.length > 0;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1">
              <Label htmlFor="bv-name" className="text-xs">Name</Label>
              <Input id="bv-name" value={view.name} onChange={e => setView(v => ({ ...v, name: e.target.value }))} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="bv-slug" className="text-xs">Address name</Label>
              <Input id="bv-slug" value={view.slug} placeholder="pit-monitor"
                onChange={e => setView(v => ({ ...v, slug: e.target.value.toLowerCase() }))} />
              <p className="text-xs text-muted-foreground">Opens at /boards/view/{view.slug || 'address-name'}</p>
            </div>
          </div>
          <div className="space-y-1 max-w-40">
            <Label htmlFor="bv-columns" className="text-xs">Columns</Label>
            <Select value={String(view.columns)} onValueChange={v => setColumns(Number(v))}>
              <SelectTrigger id="bv-columns"><SelectValue /></SelectTrigger>
              <SelectContent>
                {[1, 2, 3].map(n => <SelectItem key={n} value={String(n)}>{n}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <p className="text-sm font-medium">Panels, in order</p>
            {view.panels.map((panel, i) => (
              <div key={i} className="flex flex-wrap items-end gap-2 rounded-lg border p-2">
                <div className="space-y-1">
                  <Label htmlFor={`bv-type-${i}`} className="text-xs">Panel</Label>
                  <Select value={panel.type} onValueChange={t => setPanel(i, withType(panel, t as BoardPanelType))}>
                    <SelectTrigger id={`bv-type-${i}`} className="w-48"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {PANEL_TYPES.map(t => <SelectItem key={t.type} value={t.type}>{t.label}</SelectItem>)}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1">
                  <Label htmlFor={`bv-span-${i}`} className="text-xs">Width</Label>
                  <Select value={String(panel.span)} onValueChange={s => setPanel(i, { ...panel, span: Number(s) })}>
                    <SelectTrigger id={`bv-span-${i}`} className="w-28"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {Array.from({ length: view.columns }, (_, n) => n + 1).map(n => (
                        <SelectItem key={n} value={String(n)}>{n === 1 ? '1 column' : `${n} columns`}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                {panel.type === 'LIVE_TOP' && (
                  <div className="space-y-1">
                    <Label htmlFor={`bv-top-${i}`} className="text-xs">Places</Label>
                    <Input id={`bv-top-${i}`} type="number" min={1} max={30} className="w-20 h-9" value={panel.top ?? 10}
                      onChange={e => setPanel(i, { ...panel, top: parseInt(e.target.value, 10) || 10 })} />
                  </div>
                )}
                <div className="ml-auto flex">
                  <Button type="button" variant="ghost" size="icon-sm" aria-label={`Move panel ${i + 1} up`}
                    disabled={i === 0} onClick={() => move(i, -1)}><ArrowUp className="h-4 w-4" /></Button>
                  <Button type="button" variant="ghost" size="icon-sm" aria-label={`Move panel ${i + 1} down`}
                    disabled={i === view.panels.length - 1} onClick={() => move(i, 1)}><ArrowDown className="h-4 w-4" /></Button>
                  <Button type="button" variant="ghost" size="icon-sm" aria-label={`Remove panel ${i + 1}`}
                    onClick={() => setView(v => ({ ...v, panels: v.panels.filter((_, j) => j !== i) }))}>
                    <Trash2 className="h-4 w-4 text-destructive" />
                  </Button>
                </div>
                <p className="w-full text-xs text-muted-foreground">
                  {PANEL_TYPES.find(t => t.type === panel.type)?.help}
                </p>
              </div>
            ))}
            <Button type="button" variant="outline" size="sm" disabled={view.panels.length >= 12}
              onClick={() => setView(v => ({ ...v, panels: [...v.panels, { type: 'LIVE_TIMING', top: null, span: 1 }] }))}>
              <Plus className="h-4 w-4 mr-1" />Add panel
            </Button>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button disabled={!canSave || saving} onClick={() => void submit()}>Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
