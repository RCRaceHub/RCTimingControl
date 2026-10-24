/** The first word of a name, for a call that names drivers briefly (#482). */
export function firstNameOf(name: string): string {
  return name.trim().split(/\s+/)[0] ?? '';
}
