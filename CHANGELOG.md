# Journal des modifications

Toutes les évolutions notables de GESCOM sont consignées ici.

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le projet
applique le [versionnage sémantique](https://semver.org/lang/fr/) :

- **MAJEUR** : changement incompatible du contrat de l'API ou du schéma ;
- **MINEUR** : fonctionnalité ajoutée sans rien casser ;
- **CORRECTIF** : correction ou mise à jour sans effet sur l'usage.

La version de référence est celle du `pom.xml`. `FRONT/package.json` porte la même,
ce que la CI vérifie. Chaque version mise en production reçoit un tag Git `vX.Y.Z`.

## [1.0.1] - Non publiée

### Sécurité
- Spring Boot 3.2.0 → 3.5.16 : la branche 3.2 ne recevait plus de correctifs.
  springdoc passe en 2.8.17 et Flyway en 11, désormais piloté par Spring Boot.
- Dépendances du frontal mises à jour (`npm audit fix`) : 13 vulnérabilités → 3
  modérées, limitées à l'outillage de build (Tailwind 3).
- Mises à jour mineures groupées par Dependabot : jjwt 0.12.7, springdoc 2.9.1,
  stripe-java 24.24.0 (#17) ; React 19.3, react-router 7.18.4, recharts 3.10, i18next
  et l'outillage de lint et de build (#18).

### Ajouté
- CI : rejeu des migrations Flyway sur PostgreSQL 17 vierge, suivi d'un démarrage
  complet de l'application.
- CI : contrôle que `pom.xml` et `FRONT/package.json` portent la même version SemVer.
- Dependabot pour Maven, npm, Docker et GitHub Actions.
- Maven Wrapper (`mvnw`), qui fixe la version de Maven à 3.9.11.
- `FRONT/.nvmrc` et `engines.node` : Node 24, comme le Dockerfile et Render.

### Modifié
- Le document OpenAPI lit sa version dans le pom (build-info) au lieu d'une valeur en dur.
- Images Docker de base épinglées par empreinte (`@sha256`).
- Ponctuation uniformisée dans les commentaires, la documentation et les messages.

## [1.0.0] - 2026-10-08

Version en production sur Render et Neon, présentée au jury.

- Gestion commerciale complète : clients, produits, catégories, commandes, factures,
  livraisons, paiements par carte, stock et retours clients.
- Plateforme SaaS multi-entreprises : cloisonnement par `company_id`, rôle
  `SUPER_ADMIN`, back-office `/platform` (formules, abonnements, paiements, support).
- Schéma versionné par Flyway (migrations V1 à V22), interface en français, anglais
  et néerlandais.

[1.0.1]: https://github.com/Amadoumacka224/Gescom/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Amadoumacka224/Gescom/releases/tag/v1.0.0
