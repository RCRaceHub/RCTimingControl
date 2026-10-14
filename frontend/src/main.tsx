import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';

// Follow the OS colour scheme; there is no manual toggle
if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
  document.documentElement.classList.add('dark');
}

// Pages load on demand (App.tsx). After an upgrade, a tab left open (a board, the announcer) still asks for the
// old build's page files, which are gone: reload once to pick up the new build, but not over and over.
window.addEventListener('vite:preloadError', (event) => {
  const lastReload = Number(sessionStorage.getItem('rctiming.preloadReload') ?? 0);
  if (Date.now() - lastReload < 60_000) return;
  event.preventDefault();
  sessionStorage.setItem('rctiming.preloadReload', String(Date.now()));
  window.location.reload();
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
