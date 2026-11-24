import '@testing-library/jest-dom/vitest';

// Mock ResizeObserver (required by Radix UI components in jsdom)
globalThis.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
};

// A laptop-width window, so the run order starts expanded; narrow-screen tests set their own width
window.innerWidth = 1280;
