import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Les appels /api du front sont relayés vers le backend Spring Boot.
      // 127.0.0.1 (et non localhost) pour éviter une résolution IPv6 (::1)
      // alors que Spring Boot n'écoute que sur l'IPv4 -> ECONNREFUSED ::1:8085
      '/api': 'http://127.0.0.1:8085',
    },
  },
})
