/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  test: {
    environment: 'jsdom',
    setupFiles: './src/test-setup.js',
    globals: true,
    // Windows: the default `forks` pool crashes intermittently across multiple
    // test files in this environment; `threads` is stable.
    pool: 'threads',
  },
})
