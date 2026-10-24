import type { DragEvent } from 'react';

/** The drag type race control uses for a driver being moved between heats or finals (#482). */
const ENTRY_DRAG_TYPE = 'application/x-rctc-entry';

/** A driver picked up from the race (heat or final) they are in. */
export interface DraggedEntry {
  raceId: number;
  entryId: number;
}

/** Starts dragging a driver. Dragging only offers a move: the buttons beside each driver still work. */
export function startEntryDrag(event: DragEvent, dragged: DraggedEntry) {
  event.dataTransfer.setData(ENTRY_DRAG_TYPE, JSON.stringify(dragged));
  event.dataTransfer.effectAllowed = 'move';
}

/** Whether what is being dragged over is a driver picked up by {@link startEntryDrag}. */
export function isEntryDrag(event: DragEvent): boolean {
  return event.dataTransfer.types.includes(ENTRY_DRAG_TYPE);
}

/** The driver dropped, or null when the drop is not one of ours or is malformed. */
export function readEntryDrag(event: DragEvent): DraggedEntry | null {
  try {
    const parsed = JSON.parse(event.dataTransfer.getData(ENTRY_DRAG_TYPE)) as Partial<DraggedEntry>;
    return typeof parsed.raceId === 'number' && typeof parsed.entryId === 'number'
      ? { raceId: parsed.raceId, entryId: parsed.entryId }
      : null;
  } catch {
    return null;
  }
}
