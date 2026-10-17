import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'path';
import { docsSearchIndex } from './src/docs/docs-index-plugin';

export default defineConfig({
  plugins: [react(), tailwindcss(), docsSearchIndex(path.resolve(__dirname, '../docs'))],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    // The docs site reads ../docs/*.md (#326)
    fs: { allow: ['..'] },
    proxy: {
      '/api': 'http://localhost:8080',
      '/storage': 'http://localhost:8080',
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    exclude: ['node_modules', 'e2e/**'],
  },
});
