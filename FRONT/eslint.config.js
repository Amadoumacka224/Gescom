import js from '@eslint/js'
import globals from 'globals'
import react from 'eslint-plugin-react'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{js,jsx}'],
    extends: [
      js.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
      parserOptions: {
        ecmaVersion: 'latest',
        ecmaFeatures: { jsx: true },
        sourceType: 'module',
      },
    },
    plugins: { react },
    settings: { react: { version: 'detect' } },
    rules: {
      // Sans cette règle, no-unused-vars ignore le JSX : un identifiant qui ne sert que dans
      // du balisage passe pour mort. C'est ce qui faisait signaler `motion` dans seize
      // fichiers alors qu'il y est bien utilisé, sous la forme <motion.div>. Les supprimer
      // aurait casse l'interface.
      'react/jsx-uses-vars': 'error',
      // Même chose pour les composants eux-mêmes : <Button /> compte désormais comme un
      // usage, ce qui rend inutile le varsIgnorePattern '^[A-Z_]' qui les exemptait tous en
      // bloc — et masquait du même coup les imports réellement morts.
      'no-unused-vars': 'error',
    },
  },
  // Les scripts de vérification (parité et résolution des clés i18n) tournent sous Node, pas
  // dans le navigateur : ils lisent le système de fichiers et sortent en code d'erreur.
  {
    files: ['scripts/**/*.js'],
    languageOptions: {
      globals: globals.node,
    },
  },
])
