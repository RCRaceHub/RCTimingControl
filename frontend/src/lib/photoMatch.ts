/** A person or car a picture can be saved for. */
export interface PhotoTarget {
  id: number;
  label: string;
}

/** Lower case, accents dropped, punctuation turned to spaces: "Zoë_O'Neil-1.JPG" becomes "zoe o neil 1". */
function words(text: string): string[] {
  return text
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .split(/[^\p{L}\p{N}]+/u)
    .filter(Boolean);
}

/** A file name without its picture extension; a driver label is never stripped, so "A.Jones" keeps its A. */
function withoutExtension(fileName: string): string {
  return fileName.replace(/\.(jpe?g|png)$/i, '');
}

/** The words of a name in a fixed order, so "Rowe Alex" and "Alex Rowe" give the same key. */
function key(text: string): string {
  return words(text).sort().join(' ');
}

/**
 * Finds who a picture file is for by its name (#695): "Alex Rowe.jpg", "alex_rowe.png" and "rowe-alex.jpg" all match
 * a target labelled "Alex Rowe". Trailing numbers such as "alex_rowe_2" are ignored. A name that fits no one, or fits
 * more than one (two drivers with the same name), gives null so the official picks.
 */
export function matchTarget(fileName: string, targets: PhotoTarget[]): PhotoTarget | null {
  const name = withoutExtension(fileName);
  const withoutCounter = words(name).filter((w, i, all) => !(i === all.length - 1 && all.length > 1 && /^\d+$/.test(w)));
  const wanted = [...withoutCounter].sort().join(' ');
  const full = key(name);
  if (!wanted && !full) return null;
  const found = targets.filter(t => {
    const k = key(t.label);
    return k === wanted || k === full;
  });
  return found.length === 1 ? found[0] : null;
}
