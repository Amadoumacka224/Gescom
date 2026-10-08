# GESCOM - Client web

Interface de gestion commerciale : SPA React servie par Vite, qui consomme l'API Spring Boot
du dépôt parent. Le README de la racine documente l'API, le déploiement et la configuration
d'ensemble ; celui-ci ne couvre que le client.

## Technologies

- **React 19** et **React Router 7**
- **Vite 7** - serveur de développement et build
- **TailwindCSS 3.4** - thème maison, mode sombre par classe (`darkMode: 'class'`)
- **Axios** - client HTTP, avec injection du JWT et de l'en-tête `Accept-Language`
- **i18next / react-i18next** - français, anglais et néerlandais
- **Recharts** - graphiques du tableau de bord et des rapports
- **jsPDF** et **jspdf-autotable** - factures, bons de livraison et bons de retour
- **@zxing/browser** - lecture de codes-barres à la caisse
- **Lucide React**, **Framer Motion**, **react-hot-toast** - icônes, animations, notifications

## Démarrage

```bash
npm install
npm run dev
```

Le serveur écoute sur `http://localhost:5173`. Il relaie `/api` vers l'API Spring Boot sur
`http://127.0.0.1:8085`, qui doit donc tourner en parallèle (`mvn spring-boot:run` à la
racine). L'adresse est écrite en `127.0.0.1` et non `localhost` : ce dernier peut se résoudre
en IPv6 alors que Spring Boot n'écoute qu'en IPv4.

En déploiement, le SPA et l'API sortent sous le même domaine et `/api` reste relatif - il n'y
a aucune variable de build à définir. `VITE_API_URL` n'existe que pour le cas contraire, où le
backend est joignable à une autre origine.

## Scripts

| Commande | Rôle |
|---|---|
| `npm run dev` | Serveur de développement avec rechargement à chaud |
| `npm run build` | Build de production dans `dist/` |
| `npm run preview` | Sert le build localement, avec le même relais `/api` |
| `npm run lint` | ESLint sur l'ensemble des sources |
| `npm run check:i18n` | Contrôle des traductions - **à lancer après toute retouche d'un catalogue** |

`check:i18n` enchaîne deux vérifications complémentaires : `check-i18n.js` compare les trois
catalogues entre eux (une clé présente en français doit l'être en anglais et en néerlandais),
`check-i18n-keys.js` confronte les clés effectivement appelées par `t('…')` au catalogue de
référence. La première ne verrait pas un `t('orders.tittle')` mal orthographié, qui s'affiche
alors en clé brute à l'écran. Les deux tournent en intégration continue.

## Comptes

Aucun identifiant n'est publié ici. Les comptes sont ceux de l'API : en développement, ceux
que sèment les migrations Flyway du backend ; en déploiement, celui créé au premier démarrage
depuis les variables `PLATFORM_ADMIN_*`. Voir le README de la racine.

## Structure

```
FRONT/
├── src/
│   ├── pages/           # Un écran par route ; platform/ pour le back-office SaaS
│   ├── components/      # Composants partagés entre écrans
│   ├── layouts/         # Ossature applicative (MainLayout) et back-office (PlatformLayout)
│   ├── services/        # Appels HTTP, un module par domaine ; axios.js porte les intercepteurs
│   ├── context/         # AuthContext - session, rôle et déconnexion
│   ├── hooks/           # Thème, paramètres, notifications
│   ├── constants/       # Libellés et correspondances de statuts
│   ├── utils/           # Formatage, totaux, génération des PDF, export CSV
│   └── i18n/locales/    # Catalogues fr / en / nl
├── scripts/             # Contrôles i18n exécutés par npm run check:i18n
├── public/              # Favicon, robots.txt, sitemap.xml
├── tailwind.config.js   # Palette, jetons de badges et de tuiles, mode sombre
└── vite.config.js       # Découpage des paquets et relais /api
```

## Build

`npm run build` découpe les dépendances en paquets séparés plutôt que de tout empiler dans le
point d'entrée : socle React, graphiques, scanner, icônes, animations et i18n. Les modules
lourds mais rarement atteints - jsPDF en tête - sont chargés à la demande. Les commentaires de
`vite.config.js` détaillent pourquoi certaines dépendances sont nommées et d'autres non.
