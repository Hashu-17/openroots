/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      // ws: true also proxies the live telemetry WebSocket.
      '/api': { target: 'http://localhost:8080', ws: true },
    },
  },
  // Unit tests live in tests/unit, mirroring src/ (see docs/testing/README.md).
  test: {
    dir: 'tests/unit',
    environment: 'jsdom',
    setupFiles: ['tests/unit/setup.ts'],
  },
})
