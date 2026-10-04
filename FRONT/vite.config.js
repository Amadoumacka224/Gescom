import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * Découpe les dépendances en plusieurs fichiers pour que le navigateur
 * garde en cache celles qui ne changent pas.
 *
 * jspdf, jspdf-autotable, html2canvas et dompurify ne sont pas listés ici
 * exprès : ils sont déjà chargés à la demande (au premier PDF). Les nommer
 * les ferait charger dès l'ouverture de l'application.
 *
 * recharts et @zxing peuvent rester nommés : ils ne servent que dans des
 * écrans eux-mêmes chargés à la demande.
 */
function manualChunks(id) {
  if (!id.includes('node_modules')) return

  // Graphiques (recharts et ses dépendances d3)
  if (id.includes('recharts') || id.includes('/d3-') || id.includes('victory-vendor')) return 'charts'

  // Scanner de codes-barres
  if (id.includes('@zxing')) return 'scanner'

  if (id.includes('framer-motion')) return 'motion'
  if (id.includes('lucide-react')) return 'icons'
  if (id.includes('i18next')) return 'i18n'

  // React en dernier, sinon ce test attraperait d'autres paquets contenant « react »
  if (id.includes('react-router') || id.includes('react-dom') || id.includes('/react/')) {
    return 'react-vendor'
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: { manualChunks },
    },
  },
  server: {
    proxy: {
      // Envoie les appels /api vers le backend Spring Boot.
      // 127.0.0.1 plutôt que localhost, car Spring Boot n'écoute qu'en IPv4.
      '/api': 'http://127.0.0.1:8085',
    },
  },
  // Même chose pour `npm run preview`, pour tester le build de production en local.
  preview: {
    proxy: {
      '/api': 'http://127.0.0.1:8085',
    },
  },
})
