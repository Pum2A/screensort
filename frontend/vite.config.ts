import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    // Native output contains bundled JVM/PostgreSQL/AI files, not React source.
    watch: {ignored: ['**/src-tauri/**']},
    proxy: {
      '/api': {
        target: process.env.SCREENSORT_BACKEND_URL ?? 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
