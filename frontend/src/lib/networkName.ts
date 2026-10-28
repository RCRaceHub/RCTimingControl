/** What to run to give the laptop itself this name, for the operating system of the browser being used. */
export function renameCommand(userAgent: string, label: string): { system: string; command: string } {
  if (/windows/i.test(userAgent)) {
    return { system: 'Windows', command: `Rename-Computer -NewName ${label}   (in PowerShell as administrator, then restart)` };
  }
  if (/mac/i.test(userAgent)) {
    return { system: 'macOS', command: `sudo scutil --set LocalHostName ${label}` };
  }
  return { system: 'Linux', command: `sudo hostnamectl set-hostname ${label}` };
}
