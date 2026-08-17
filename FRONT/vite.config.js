import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * Regroupement des dépendances en paquets distincts.
 *
 * Sans cela, tout node_modules atterrit dans le paquet d'entrée : 2,5 Mo d'un seul
 * tenant, réinvalidés en totalité au moindre changement de code applicatif. Le
 * découpage ci-dessous suit les frontières de bibliothèques, seul critère qui ne
 * crée pas de dépendances circulaires entre paquets.
 *
 * Absences volontaires : jspdf, jspdf-autotable, html2canvas et dompurify. Tous quatre sont
 * atteints par import dynamique (`pdfGenerator.js` et `exportData.js` chargent jspdf au
 * premier PDF, jspdf charge lui-même les deux autres), et Rollup en fait donc déjà des
 * paquets asynchrones.
 *
 * Les nommer ici les ferait basculer dans le graphe STATIQUE de l'entrée : mesuré, une règle
 * `if (id.includes('jspdf')) return 'pdf'` suffit à faire réapparaître les 418 ko de jspdf
 * dans les modulepreload de index.html, alors même que plus aucun module ne l'importe
 * statiquement. Un paquet manuel se déclare pour REGROUPER des dépendances déjà chargées au
 * démarrage, jamais pour nommer une dépendance qu'on vient de rendre paresseuse.
 *
 * recharts et @zxing sont un cas différent et restent nommés : ils ne sont atteints qu'à
 * travers des modules eux-mêmes paresseux (React.lazy sur les pages et sur la modale du
 * scanner), si bien que tout le sous-arbre est asynchrone et que le nommage n'y change rien.
 */
function manualChunks(id) {
  if (!id.includes('node_modules')) return

  // Graphiques : recharts et toute la famille d3 qu'il tire derrière lui.
  if (id.includes('recharts') || id.includes('/d3-') || id.includes('victory-vendor')) return 'charts'

  // Lecture de codes-barres : une seule modale, sur un seul écran.
  if (id.includes('@zxing')) return 'scanner'

  if (id.includes('framer-motion')) return 'motion'
  if (id.includes('lucide-react')) return 'icons'
  if (id.includes('i18next')) return 'i18n'

  // Socle React. En queue de liste : react-dom et react-router-dom contiennent
  // « react », un test placé plus haut capturerait des paquets qui ne sont pas lui.
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
      // Les appels /api du front sont relayés vers le backend Spring Boot.
      // 127.0.0.1 (et non localhost) pour éviter une résolution IPv6 (::1)
      // alors que Spring Boot n'écoute que sur l'IPv4 -> ECONNREFUSED ::1:8085
      '/api': 'http://127.0.0.1:8085',
    },
  },
  // Même relais pour `npm run preview`, qui sert le build de production en local. Sans lui,
  // la seule façon d'essayer le résultat compilé était de le déployer : les défauts propres
  // au build — découpage des paquets, chargement à la demande — ne se voient pas en
  // développement, où Vite sert les modules un par un.
  preview: {
    proxy: {
      '/api': 'http://127.0.0.1:8085',
    },
  },
})
