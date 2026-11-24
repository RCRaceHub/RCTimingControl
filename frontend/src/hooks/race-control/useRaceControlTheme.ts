import { useEffect, useState } from 'react';

const STORAGE_KEY = 'rc-race-control-theme';

type Theme = 'light' | 'dark';

function osTheme(): Theme {
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches === true ? 'dark' : 'light';
}

function readTheme(): Theme {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'light' || stored === 'dark') return stored;
  } catch {
    // Storage refused: follow the operating system
  }
  return osTheme();
}

/**
 * A light or dark theme for the race control screens (#773), chosen on this device and kept in local storage; until
 * a choice is made it follows the operating system, as the rest of the app does. Applied by the {@code dark} class
 * on the page, which is put back to the operating system's setting when the race control screens are left.
 */
export function useRaceControlTheme() {
  const [theme, setTheme] = useState<Theme>(readTheme);

  // Put the page back as it was found when race control is left; declared first so it reads the class before the theme is applied
  useEffect(() => {
    const root = document.documentElement;
    const initiallyDark = root.classList.contains('dark');
    return () => {
      root.classList.toggle('dark', initiallyDark);
    };
  }, []);

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark');
  }, [theme]);

  function toggle() {
    const next: Theme = theme === 'dark' ? 'light' : 'dark';
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // The choice lasts until the page reloads
    }
    setTheme(next);
  }

  return { theme, toggle };
}
