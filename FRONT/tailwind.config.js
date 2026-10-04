/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  // Ces classes sont construites dynamiquement (`badge-${tone}`) : Tailwind ne les
  // voit pas dans le code, on les déclare donc ici pour qu'il les garde.
  safelist: [
    'badge-success', 'badge-warning', 'badge-danger',
    'badge-info', 'badge-accent', 'badge-neutral',
    'stat-tile-success', 'stat-tile-warning', 'stat-tile-danger',
    'stat-tile-info', 'stat-tile-accent', 'stat-tile-neutral',
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: [
          'Inter',
          '-apple-system',
          'BlinkMacSystemFont',
          'Segoe UI',
          'Roboto',
          'Helvetica Neue',
          'Arial',
          'sans-serif',
        ],
      },
      boxShadow: {
        // Ombres légères, teintées au bleu nuit de la charte plutôt qu'au noir.
        card: '0 1px 2px 0 rgba(26, 39, 64, 0.04), 0 1px 3px 0 rgba(26, 39, 64, 0.06)',
        'card-hover': '0 4px 12px -2px rgba(26, 39, 64, 0.08), 0 2px 6px -2px rgba(26, 39, 64, 0.05)',
        soft: '0 2px 8px -2px rgba(26, 39, 64, 0.08)',
        elevated: '0 12px 32px -8px rgba(26, 39, 64, 0.16), 0 4px 12px -4px rgba(26, 39, 64, 0.08)',
      },
      /* Couleurs de la charte :
       *   #2196f3 bleu vif    → `primary-500` (boutons, liens)
       *   #1f77b4 bleu acier  → `primary-600` (survol)
       *   #26395e bleu nuit   → `secondary-700` et `gray-800` (surfaces sombres)
       *   #f4f6fa blanc cassé → `gray-50` (fond de l'application)
       *
       * `blue` et `gray` sont redéfinis pour que les pages qui les utilisent
       * suivent la charte sans avoir à les modifier.
       */
      colors: {
        primary: {
          50: '#eef7fe',
          100: '#d8ecfd',
          200: '#b4dbfb',
          300: '#82c4f8',
          400: '#4aa8f6',
          500: '#2196f3',
          600: '#1f77b4',
          700: '#1b6294',
          800: '#1a5178',
          900: '#1a4463',
          950: '#0f2b42',
        },
        // Copie de `primary`, pour les `blue-*` utilisés dans les pages.
        blue: {
          50: '#eef7fe',
          100: '#d8ecfd',
          200: '#b4dbfb',
          300: '#82c4f8',
          400: '#4aa8f6',
          500: '#2196f3',
          600: '#1f77b4',
          700: '#1b6294',
          800: '#1a5178',
          900: '#1a4463',
          950: '#0f2b42',
        },
        // Couleur `accent` des badges et graphiques : de l'indigo, bien distinct du bleu de marque.
        violet: {
          50: '#eef2ff',
          100: '#e0e7ff',
          200: '#c7d2fe',
          300: '#a5b4fc',
          400: '#818cf8',
          500: '#6366f1',
          600: '#4f46e5',
          700: '#4338ca',
          800: '#3730a3',
          900: '#312e81',
          950: '#1e1b4b',
        },
        secondary: {
          50: '#f2f5fa',
          100: '#e3e9f3',
          200: '#c7d4e7',
          300: '#9db2d1',
          400: '#6d8ab5',
          500: '#4c6b9b',
          600: '#3a5480',
          700: '#26395e',
          800: '#22304d',
          900: '#1b2740',
          950: '#111a2c',
        },
        /* Gris bleutés. En thème sombre : 900 = fond, 800 = cartes, 700 = champs et bordures.
         * Garder cet ordre, sinon les cartes se confondent avec le fond. */
        gray: {
          50: '#f4f6fa',
          100: '#e8ecf4',
          200: '#d3dae8',
          300: '#b0bcd2',
          400: '#8496b4',
          500: '#647697',
          600: '#4d5e7d',
          700: '#3b4b68',
          800: '#26395e',
          900: '#1a2740',
          950: '#111a2c',
        },
      },
      animation: {
        'fade-in': 'fadeIn 0.5s ease-in-out',
        'slide-in': 'slideIn 0.3s ease-out',
        'scale-in': 'scaleIn 0.2s ease-out',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        slideIn: {
          '0%': { transform: 'translateY(-10px)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' },
        },
        scaleIn: {
          '0%': { transform: 'scale(0.95)', opacity: '0' },
          '100%': { transform: 'scale(1)', opacity: '1' },
        },
      },
    },
  },
  plugins: [],
}
