# GESCOM

[![CI](https://github.com/Amadoumacka224/Gescom/actions/workflows/ci.yml/badge.svg)](https://github.com/Amadoumacka224/Gescom/actions/workflows/ci.yml)

Application SaaS de gestion commerciale et de stock pour les TPE et PME : une API REST Spring Boot et un client React, dans le même dépôt.

## Le problème

Une petite entreprise qui vend des produits suit souvent ses ventes, son stock et ses factures dans plusieurs outils qui ne se parlent pas. Le stock n'est pas à jour, on facture une commande déjà annulée, on ne sait plus qui a fait quoi. GESCOM rassemble ce parcours dans une seule application : de la commande à la livraison, chaque étape met à jour le stock et laisse une trace.

L'application est multi-entreprises. Plusieurs sociétés clientes partagent la même installation, chacune ne voit que ses propres données, et un back-office permet à l'opérateur de la plateforme de gérer les entreprises, les formules et les abonnements.

## Fonctionnalités

**Espace entreprise** (rôles `ADMIN` et `CAISSIER`)

- Clients, catégories et produits, avec import en lot et export CSV.
- Commandes avec un cycle de vie strict : `PENDING → CONFIRMED → INVOICED → DELIVERED`, annulation possible avant la livraison.
- Factures (remises, TVA, paiements partiels, retards) et livraisons. Une livraison n'est possible qu'après la facture.
- Stock : le stock est décrémenté à la confirmation de la commande, sous verrou pessimiste, et rendu à l'annulation. Chaque mouvement est historisé, et on peut saisir des entrées, sorties, ajustements et casses.
- Retours clients : à partir du numéro de commande ou de facture, avec contrôle des quantités encore retournables et remboursement calculé sur le prix réellement payé.
- Écran de caisse avec lecture de codes-barres par la caméra.
- Paiement par carte via Stripe, en mode test uniquement (simulé par défaut).
- Tableau de bord, rapports, export PDF des documents.
- Journal d'activité des utilisateurs.
- Interface et messages d'erreur en français, anglais et néerlandais.

**Back-office de la plateforme** (rôle `SUPER_ADMIN`)

- Entreprises clientes : création, suspension (qui coupe l'accès de tous leurs utilisateurs), réactivation.
- Formules, abonnements et paiements, avec un tableau de bord (MRR, résiliations).
- Notifications, support et journal d'activité de la plateforme.

## Choix techniques

- **Cloisonnement des données** : base partagée, une colonne `company_id` sur chaque table métier. L'isolation est assurée par l'infrastructure, pas par chaque service : un filtre Hibernate sur les requêtes, un dépôt qui refuse le chargement par id d'une ligne étrangère, un écouteur qui renseigne l'entreprise à l'enregistrement. L'entreprise vient de l'utilisateur chargé en base, jamais d'une valeur envoyée par le client.
- **Sécurité** : JWT sans session, mots de passe BCrypt (coût 12), limitation des tentatives de connexion, autorisations par rôle sur chaque méthode (`@PreAuthorize`) et par préfixe d'URL.
- **Règles métier** : le cycle de vie des commandes est une machine à états (`ALLOWED_TRANSITIONS`). Les opérations de stock concurrentes passent par un verrou pessimiste.
- **Schéma** : Flyway est seul propriétaire du schéma. Hibernate ne fait que le valider au démarrage.
- **Architecture** : `Controller → Mapper → Service → Repository`. Les contrôleurs ne renvoient jamais d'entités, et les erreurs sont traduites en réponses HTTP par un gestionnaire global.

## Démarrage rapide

Prérequis : Java 17+, Node 24+, PostgreSQL avec une base `GESCOM_2` (voir [Configuration](#configuration)).

```bash
# API, sur http://localhost:8085
./mvnw spring-boot:run

# Client, sur http://localhost:5173 (relaie /api vers l'API)
cd FRONT
npm ci
npm run dev
```

Sans PostgreSQL ni Java installés, `docker compose up -d --build` démarre l'ensemble (voir [Déploiement](#déploiement)).

## Tests et qualité

```bash
./mvnw clean install          # compilation, tests, rapport de couverture (target/site/jacoco)
./mvnw test -Dtest=SecurityRulesTest

cd FRONT
npm run lint
npm test                      # tests Vitest
npm run check:i18n            # parité des traductions fr/en/nl et clés utilisées
npm run build
```

L'API a 122 tests JUnit :

- des tests unitaires Mockito sur les règles métier (paiements, remises, annulation de commande, retours clients, suppression de facture, mise à jour de produit, export CSV) ;
- des tests d'intégration Spring sur base H2 pour le cloisonnement entre entreprises, le périmètre des caissiers, la cohérence du tableau de bord et les règles d'accès HTTP avec de vrais jetons JWT.

Le client React a 36 tests Vitest (jsdom et Testing Library) :

- les calculs de montants, qui doivent donner le même résultat que le backend ;
- la validation du formulaire client ;
- le formatage selon la langue ;
- le statut affiché d'une commande ;
- la lecture des erreurs de l'API ;
- le client HTTP : jeton joint à chaque requête, session effacée sur un 401 ;
- la redirection des routes protégées.

Les écrans eux-mêmes ne sont pas testés.

Chaque poussée sur `main` ou `develop` lance l'intégration continue, en quatre tâches indépendantes :

- `mvn clean install` pour l'API (compilation, tests, rapport de couverture) ;
- rejeu des migrations Flyway sur un PostgreSQL 17 vierge ;
- contrôle de la cohérence des versions entre l'API et le client ;
- lint, tests, contrôle des traductions et build du SPA.

## Limites connues

- La couverture de tests de l'API reste partielle (environ 24 % des instructions). Les zones les plus sensibles sont couvertes : cloisonnement, stock, paiements, retours.
- Côté client, seuls la logique partagée et quelques composants sont testés. Les écrans ne le sont pas, et le lint signale encore 78 avertissements.
- Le jeton JWT est conservé dans le `localStorage`. La politique CSP stricte (`script-src 'self'`) limite le risque XSS, mais un cookie `HttpOnly` serait plus sûr.
- La déconnexion ne révoque pas le jeton : il reste valide jusqu'à son expiration (24 h par défaut).
- Seuls le journal d'activité et les mouvements de stock sont paginés côté API. Les autres listes sont renvoyées en entier.
- Stripe n'est intégré qu'en mode test : aucun vrai paiement n'est possible.

## Branches et versions

Le dépôt n'a que deux branches permanentes :

| Branche | Rôle |
|---|---|
| `develop` | Intégration du développement en cours. Tout le travail y est committé, et les mises à jour de Dependabot y arrivent. |
| `main` | Production. Render la redéploie à chaque poussée : elle ne reçoit que des versions terminées, par fusion de `develop`. |

Mettre une version en production :

```bash
# Sur develop : monter la version dans pom.xml et FRONT/package.json.
git checkout main
git merge --no-ff develop -m "Version X.Y.Z"
git tag -a vX.Y.Z -m "GESCOM X.Y.Z"
git push origin main vX.Y.Z
git checkout develop
```

Les deux branches sont protégées sur GitHub contre le force-push et la suppression. La version suit le [versionnage sémantique](https://semver.org/lang/fr/), et chaque mise en production porte un tag `vX.Y.Z`.

## Technologies

| Couche | Technologies |
|---|---|
| API | Java 17, Spring Boot 3.5, Spring Security, JWT (jjwt), Spring Data JPA / Hibernate, Bean Validation, Lombok, springdoc-openapi |
| Base de données | PostgreSQL 17, migrations Flyway |
| Client | React 19, Vite, Tailwind CSS, React Router, i18next, Recharts, jsPDF, ZXing (codes-barres) |
| Tests | JUnit 5, Mockito, AssertJ, Spring Boot Test, H2, JaCoCo |
| Livraison | Docker (build multi-étapes), Docker Compose, Caddy (TLS automatique), GitHub Actions, Render |

## Prérequis

Pour le développement :

- Java 17 ou supérieur (Maven est fourni par le wrapper `./mvnw`)
- PostgreSQL 12 ou supérieur
- Node 24 ou supérieur, pour le client

Pour un déploiement, Docker suffit - voir [Déploiement](#déploiement).

## Configuration

### 1. Base de données

Créer la base de données PostgreSQL (le nom par défaut attendu est `GESCOM_2`) :

```sql
CREATE DATABASE "GESCOM_2";
```

Le schéma est construit par Flyway au démarrage, à partir des migrations de `src/main/resources/db/migration` : il suffit que la base existe, l'application joue les scripts elle-même. Hibernate ne fait plus que vérifier la correspondance avec les entités (`spring.jpa.hibernate.ddl-auto=validate`) - une entité modifiée sans migration correspondante fait échouer le démarrage.

Une base qui porte déjà le schéma - celles en service avant le passage à Flyway - n'a pas à être vidée : `baseline-on-migrate` la marque au niveau `baseline-version=14.2`, c'est-à-dire « à jour », et aucun script n'est rejoué. **Ne recréez jamais une base d'exploitation pour faire démarrer l'application** : les migrations n'amorcent que les données de référence (clients, produits, catégories, comptes), pas les commandes, factures, livraisons ni paiements, qui seraient perdus.

Les migrations chargent aussi un jeu de données de départ (110 clients, 180 produits, 12 catégories) et quatre comptes de démonstration :

| Compte | Rôle |
|---|---|
| `admin` | `ADMIN` |
| `cbernard`, `mdubois`, `speeters` | `CAISSIER` |

Leurs mots de passe ne sont pas publiés ici. Ce sont des valeurs de développement, versionnées sous forme d'empreinte bcrypt dans `V1_2__insert_users_table.sql` : elles n'ont de sens que sur une base locale jetable, et doivent être considérées comme connues de quiconque a accès au dépôt.

> **Une installation en service ne doit jamais conserver ces comptes en l'état.** Changez leur mot de passe dès la première connexion (`POST /api/users/me/change-password`), ou supprimez-les une fois vos propres comptes créés. Tant que ce n'est pas fait, l'application est ouverte à qui a lu le dépôt.

Toute évolution du schéma passe par un nouveau fichier, à la suite du dernier appliqué (`V23__....sql` à ce jour), même pour corriger un script existant : Flyway vérifie la somme de contrôle des scripts déjà joués, en modifier un fait échouer le démarrage suivant.

### 2. Variables d'environnement

L'application lit l'ensemble de ses paramètres sensibles depuis l'environnement. **Toutes les variables ci-dessous doivent être définies en production** ; les valeurs par défaut présentes dans `application.properties` ne servent qu'au démarrage local et ne doivent jamais être utilisées en environnement déployé.

| Variable          | Description                                                        |
|-------------------|--------------------------------------------------------------------|
| `DB_URL`          | URL JDBC PostgreSQL                                                |
| `DB_USERNAME`     | Utilisateur PostgreSQL                                             |
| `DB_PASSWORD`     | Mot de passe PostgreSQL                                            |
| `JWT_SECRET`      | Secret JWT - **64 caractères aléatoires minimum en production**    |
| `JWT_EXPIRATION`  | Durée de validité du token (ms)                                    |
| `CORS_ORIGINS`    | Origines CORS autorisées (séparées par virgule)                    |
| `PLATFORM_ADMIN_USERNAME` | Compte propriétaire du back-office SaaS - voir ci-dessous  |
| `PLATFORM_ADMIN_EMAIL`    | Adresse de ce compte                                       |
| `PLATFORM_ADMIN_PASSWORD` | Mot de passe initial de ce compte                          |

Exemple (Linux/macOS) :

```bash
export DB_URL=jdbc:postgresql://localhost:5432/GESCOM_2
export DB_USERNAME=postgres
export DB_PASSWORD=********
export JWT_SECRET=$(openssl rand -hex 64)
export CORS_ORIGINS=https://app.example.com
```

#### Compte propriétaire et développement local

Le compte `SUPER_ADMIN` du back-office n'est semé par aucune migration : il faudrait y versionner une empreinte de mot de passe en dur, identique sur toutes les installations. Il est créé au premier démarrage par `PlatformAdminBootstrap`, à partir des trois variables `PLATFORM_ADMIN_*`, et jamais réécrit ensuite - une rotation faite depuis le back-office survit donc aux redéploiements.

Conséquence en local : `docker compose up` lit `.env` et fournit ces variables, mais **`mvn spring-boot:run` ne lit pas `.env`**. Sans elles, aucun compte n'est créé et le back-office reste inaccessible après chaque recréation de la base.

Pour y remédier une fois pour toutes, copiez le modèle fourni et renseignez-le :

```bash
cp application-local.properties.example application-local.properties
```

`application.properties` l'importe avec `spring.config.import=optional:file:./application-local.properties`. Le fichier est ignoré par Git, et `optional:` garantit que son absence ne change rien en conteneur, sur Render ou en CI. N'y recopiez pas `.env` tel quel : celui-ci vise le PostgreSQL du conteneur (`DB_USERNAME=gescom`) et casserait la connexion à votre base locale.

## Installation et démarrage

```bash
# 1. Cloner le projet
# 2. Compiler et installer les dépendances
mvn clean install

# 3. Lancer l'application
mvn spring-boot:run
```

L'API sera accessible sur `http://localhost:8085/`.

## Déploiement

Le dépôt porte de quoi déployer les deux applications sur une machine unique : un `Dockerfile`
multi-étapes, un `docker-compose.yml` et un `Caddyfile`. Seuls Docker et un nom de domaine
pointant sur la machine sont requis - ni Java, ni Node, ni PostgreSQL à installer sur l'hôte.

### Principe

Trois conteneurs sur un réseau interne, un seul point d'entrée :

```
Internet ──443──> web (Caddy)  ──/api/*──> api (Spring Boot :8085) ──> db (PostgreSQL :5432)
                    │
                    └─ tout le reste : le SPA React compilé
```

`web` est le seul service à publier des ports. **L'API et la base ne sont joignables depuis
aucune interface de la machine**, uniquement par le réseau Docker interne. Deux conséquences
utiles : PostgreSQL n'est pas exposé, et `/swagger-ui/**` non plus puisque Caddy ne relaie que
`/api/*`.

Le SPA et l'API sortent sous **le même domaine**. Le navigateur n'émet donc aucune requête
inter-origines : pas de préflight CORS, et le JWT ne franchit jamais de frontière d'origine.
C'est ce qui rend la configuration CORS sans objet à l'usage normal, plutôt que d'avoir à la
durcir. Le client tombe sur `/api` en relatif (`FRONT/src/services/axios.js`), il n'y a pas de
variable de build à définir côté front.

Caddy obtient et renouvelle seul le certificat Let's Encrypt : aucune clé TLS à gérer.

### Mise en service

Le domaine n'est écrit dans aucun fichier versionné : il vient de `APP_DOMAIN` dans `.env`,
qui alimente à la fois le bloc de site du `Caddyfile` et l'origine CORS de l'API.

L'adresse `ACME_EMAIL` doit être réelle et relevée : c'est là que Let's Encrypt signale les
expirations, et une adresse en `example.com` fait carrément rejeter la création du compte
(`invalidContact - contact email has forbidden domain`), après quoi Caddy bascule sans le dire
sur l'émetteur de repli.

```bash
cp .env.example .env
chmod 600 .env          # le fichier porte les secrets de production
$EDITOR .env            # renseigner APP_DOMAIN, ACME_EMAIL, DB_PASSWORD et JWT_SECRET

docker compose up -d --build
docker compose logs -f api
```

Le premier démarrage crée la base, Flyway y joue les migrations et l'application est en ligne
en HTTPS. Les commandes courantes ensuite :

```bash
docker compose ps                  # état des services
docker compose logs -f api         # journaux de l'API
docker compose exec db psql -U gescom gescom
docker compose up -d --build       # redéployer après un git pull
```

### Secrets

Les variables du tableau de la section [Variables d'environnement](#2-variables-denvironnement)
sont fournies par le fichier `.env`, décrit dans `.env.example`. Deux rappels :

- `JWT_SECRET` signe l'ensemble des sessions - qui le connaît peut forger un jeton
  d'administrateur sans mot de passe. Le générer (`openssl rand -hex 64`), jamais l'inventer.
  Le changer révoque toutes les sessions en cours, ce qui en fait le geste d'urgence en cas de
  doute.
- `docker compose up` refuse de démarrer si `DB_PASSWORD` ou `JWT_SECRET` est vide, et l'API
  refuse un secret JWT de moins de 32 octets. Les autres valeurs par défaut
  d'`application.properties` ne font pas échouer le démarrage : l'absence d'erreur au boot ne
  prouve donc pas que toute la configuration est renseignée.

### À faire une fois en ligne

Le déploiement ne couvre pas ces deux points, qui relèvent de l'exploitation :

1. **Changer les mots de passe des comptes amorcés.** Sur une base vierge, les migrations créent
   le compte `admin` et trois comptes caissier, dont les mots de passe de développement sont
   déductibles du dépôt (voir la section Base de données). Tant que ce n'est pas fait,
   l'application est ouverte à qui a cloné le projet.
2. **Mettre en place les sauvegardes.** Aucune n'est automatique. Par exemple, en tâche cron :

   ```bash
   docker compose exec -T db pg_dump -U gescom gescom | gzip > gescom-$(date +%F).sql.gz
   ```

   Prévoir une rotation et une copie hors de la machine - une sauvegarde qui ne vit que sur le
   serveur sauvegardé ne protège de rien.

### Hôte

N'ouvrir au pare-feu que 22 (SSH), 80 et 443. Le port 80 sert au renouvellement des certificats
et à la redirection vers HTTPS, que Caddy met en place de lui-même.

## Endpoints API

> Toutes les routes sont préfixées par `/api`. Hors `/api/auth/**`, un JWT valide est requis.

### Documentation interactive

L'application démarrée, la référence complète - et essayable - se trouve sur
**<http://localhost:8085/swagger-ui.html>**. La liste ci-dessous en reste un résumé de lecture,
elle n'est pas engendrée et peut retarder sur le code ; le document OpenAPI, lui, est produit
depuis les contrôleurs eux-mêmes (`/v3/api-docs`).

Pour dérouler un appel authentifié depuis l'interface :

1. `POST /api/auth/login` avec un compte existant ;
2. copier la valeur du champ `token` de la réponse ;
3. la coller dans **Authorize**, en haut à droite - sans le préfixe `Bearer`, ajouté par l'interface.

Les essais passent alors par exactement les mêmes contrôles que n'importe quel appel : un
CAISSIER se verra refuser les routes réservées à l'ADMIN, et le cloisonnement par entreprise
s'applique aussi.

> La documentation n'est ouverte qu'en développement. Le profil `render` la désactive
> entièrement (`springdoc.*.enabled=false`, ces chemins répondent alors 404), et le déploiement
> docker-compose ne la publie pas non plus : Caddy ne relaie que `/api/*`. Publier la
> cartographie des 164 points d'entrée d'une installation en service épargnerait à un attaquant
> tout son travail de reconnaissance.

### Authentification (`/api/auth`)
- `POST /login` - Connexion (retourne un token JWT)
- `POST /logout` - Déconnexion

### Utilisateurs (`/api/users`) - ADMIN
- `GET /` - Liste des utilisateurs
- `GET /me` - Profil de l'utilisateur connecté
- `PUT /me` - Modifier son profil
- `POST /me/change-password` - Changer son mot de passe
- `GET /{id}` - Détails d'un utilisateur
- `GET /username/{username}` - Recherche par nom d'utilisateur
- `GET /role/{role}` - Filtrer par rôle
- `GET /caissiers` - Liste des caissiers
- `GET /active` - Utilisateurs actifs
- `POST /` - Créer un utilisateur
- `PUT /{id}` - Modifier un utilisateur
- `PATCH /{id}/deactivate` - Désactiver un utilisateur
- `DELETE /{id}` - Supprimer un utilisateur

### Clients (`/api/clients`)
- `GET /` - Liste des clients
- `GET /active` - Clients actifs
- `GET /{id}` - Détails d'un client
- `GET /email/{email}` - Recherche par email
- `GET /type/{type}` - Filtrer par type
- `POST /` - Créer un client
- `PUT /{id}` - Modifier un client
- `PATCH /{id}/deactivate` - Désactiver un client
- `DELETE /{id}` - Supprimer un client
- `GET /export` - Export des clients

### Catégories (`/api/categories`)
- `GET /` - Liste des catégories
- `GET /active` - Catégories actives
- `GET /{id}` - Détails d'une catégorie
- `POST /` - Créer une catégorie
- `PUT /{id}` - Modifier une catégorie
- `PATCH /{id}/toggle-status` - Activer/désactiver
- `DELETE /{id}` - Supprimer une catégorie

### Produits (`/api/products`)
- `GET /` - Liste des produits
- `GET /active` - Produits actifs
- `GET /{id}` - Détails d'un produit
- `GET /code/{code}` - Recherche par code
- `GET /category/{categoryId}` - Produits d'une catégorie
- `GET /low-stock` - Produits en rupture / stock faible
- `GET /{id}/check-stock/{quantity}` - Vérifier disponibilité
- `POST /` - Créer un produit
- `PUT /{id}` - Modifier un produit
- `PATCH /{id}/stock` - Mettre à jour le stock
- `DELETE /{id}` - Supprimer un produit
- `GET /export` - Export des produits
- `POST /import` - Import en lot

### Commandes (`/api/orders`)
- `GET /` - Liste des commandes
- `GET /{id}` - Détails d'une commande
- `GET /number/{orderNumber}` - Recherche par numéro
- `GET /client/{clientId}` - Commandes par client
- `GET /user/{userId}` - Commandes par utilisateur
- `GET /status/{status}` - Filtrer par statut
- `GET /date-range` - Filtrer par plage de dates
- `POST /` - Créer une commande
- `PUT /{id}` - Modifier une commande
- `PATCH /{id}/status` - Changer le statut
- `PATCH /{id}/cancel` - Annuler la commande
- `DELETE /{id}` - Supprimer la commande
- `GET /export` - Export des commandes

### Livraisons (`/api/deliveries`)
- `GET /` - Liste des livraisons
- `GET /{id}` - Détails d'une livraison
- `GET /number/{deliveryNumber}` - Recherche par numéro
- `GET /order/{orderId}` - Livraisons d'une commande
- `GET /status/{status}` - Filtrer par statut
- `GET /date-range` - Filtrer par plage de dates
- `POST /` - Créer une livraison
- `PUT /{id}` - Modifier une livraison
- `PATCH /{id}/status` - Changer le statut
- `PATCH /{id}/mark-delivered` - Marquer comme livrée
- `POST /{id}/create-invoice` - Générer la facture associée
- `DELETE /{id}` - Supprimer la livraison
- `GET /export` - Export des livraisons

### Factures (`/api/invoices`)
- `GET /` - Liste des factures
- `GET /{id}` - Détails d'une facture
- `GET /number/{invoiceNumber}` - Recherche par numéro
- `GET /order/{orderId}` - Factures d'une commande
- `GET /status/{status}` - Filtrer par statut
- `GET /date-range` - Filtrer par plage de dates
- `GET /overdue` - Factures en retard
- `POST /` - Créer une facture
- `PATCH /{id}/payment` - Enregistrer un paiement
- `PATCH /{id}/cancel` - Annuler une facture
- `DELETE /{id}` - Supprimer une facture

### Stock (`/api/stock`)
- `GET /movements` - Liste des mouvements
- `GET /movements/{id}` - Détails d'un mouvement
- `GET /movements/product/{productId}` - Mouvements d'un produit
- `GET /movements/type/{type}` - Filtrer par type de mouvement
- `GET /movements/date-range` - Filtrer par plage de dates
- `POST /add` - Entrée de stock
- `POST /remove` - Sortie de stock
- `POST /adjust` - Ajustement de stock
- `POST /damage` - Déclarer une casse
- `GET /low-stock` - Produits en stock faible
- `GET /out-of-stock` - Produits en rupture
- `GET /statistics` - Statistiques de stock
- `DELETE /movements/{id}` - Supprimer un mouvement
- `GET /export` - Export des mouvements

### Journal d'activité (`/api/activities`)
- `GET /` - Liste des activités
- `GET /{id}` - Détails d'une activité
- `GET /user/{userId}` - Activités d'un utilisateur
- `GET /action/{actionType}` - Filtrer par type d'action
- `GET /entity/{entity}` - Filtrer par entité
- `GET /date-range` - Filtrer par plage de dates
- `GET /caissiers` - Activités des caissiers
- `POST /` - Créer une entrée
- `DELETE /{id}` - Supprimer une entrée

### Tableau de bord (`/api/dashboard`)
- `GET /stats` - Indicateurs clés
- `GET /recent-orders` - Dernières commandes
- `GET /top-products` - Meilleures ventes
- `GET /overview` - Vue d'ensemble

### Paramètres (`/api/settings`)
- `GET /` - Lire les paramètres applicatifs
- `PUT /` - Mettre à jour les paramètres

### Retours clients (`/api/stock/returns`)
- `GET /lookup` - Retrouver une vente par numéro de commande ou de facture
- `GET /` - Liste des retours
- `GET /{id}` - Détails d'un retour
- `POST /` - Enregistrer un retour

### Paiement par carte (`/api/payments`) - Stripe, mode test
- `POST /stripe/intents` - Créer une intention de paiement
- `POST /stripe/intents/{id}/confirm` - Confirmer
- `POST /stripe/intents/{id}/cancel` - Annuler
- `GET /stripe/intents/{id}` - État d'une intention
- `GET /invoice/{invoiceId}` - Paiements d'une facture

### Back-office de la plateforme (`/api/platform`) - SUPER_ADMIN
- Entreprises : `/companies` (liste, création, modification, `suspend`, `reactivate`, `cancel`)
- Comptes : `/users` (liste, activation)
- Formules, abonnements et paiements : `/plans`, `/subscriptions`, `/payments`
- Exploitation : `/dashboard`, `/activity`, `/notifications`, `/settings`, `/support`

Le détail de ces routes est dans Swagger UI.

## Authentification JWT

Toutes les requêtes (sauf `/api/auth/**`) nécessitent un token JWT dans l'en-tête :

```
Authorization: Bearer <votre_token_jwt>
```

## Rôles utilisateurs

- **ADMIN** - Accès complet aux fonctionnalités de son entreprise
- **CAISSIER** - Accès aux opérations courantes (clients, produits, commandes, livraisons, factures)
- **SUPER_ADMIN** - Opérateur de la plateforme : accès au seul back-office `/api/platform/**`, rattaché à aucune entreprise et sans accès aux écrans métier

## Support multi-langue

L'application supporte :
- Français (fr)
- Anglais (en)
- Néerlandais (nl)

Les fichiers de messages se trouvent dans `src/main/resources/i18n/` (basename `messages`).

## Structure du projet

Ce dépôt héberge **deux applications** malgré son nom : l'API Spring Boot à la racine, et le
client React sous `FRONT/`.

```
BACK/
├── src/main/java/com/gescom/backend/
│   ├── controller/    # Contrôleurs REST (fins : ils délèguent au service et mappent)
│   ├── service/       # Logique métier
│   ├── mapper/        # Entité <-> DTO, écrits à la main (@Component)
│   ├── repository/    # Accès aux données (Spring Data JPA)
│   ├── entity/        # Entités JPA
│   ├── dto/           # DTOs, un sous-package par domaine (+ common/, auth/)
│   ├── tenancy/       # Cloisonnement multi-entreprises (filtre, contexte, garde)
│   ├── exception/     # Exceptions métier et handler global
│   ├── security/      # Filtres et configuration JWT
│   └── config/        # Configuration Spring
├── src/main/resources/
│   ├── application.properties
│   ├── db/migration/  # Scripts Flyway, joués au démarrage
│   └── i18n/          # Messages serveur (fr / en / nl)
├── src/test/java/     # Tests JUnit
├── FRONT/             # Client React + Vite + Tailwind (voir FRONT/README.md)
├── .github/workflows/ # Intégration continue (tests API, lint et build du SPA)
├── Dockerfile         # Build des deux applications, cibles `api` et `web`
├── docker-compose.yml # Déploiement autonome : web (Caddy) + api + db
├── Caddyfile          # TLS, service du SPA, relais /api
├── render.yaml        # Déploiement géré : API, SPA et base PostgreSQL
├── .env.example       # Modèle de configuration ; le .env réel n'est pas versionné
└── pom.xml
```
