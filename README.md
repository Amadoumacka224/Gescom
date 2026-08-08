# GESCOM - Backend API

Application de gestion commerciale développée avec Spring Boot.

## Technologies

- **Spring Boot 3.2.0**
- **Java 17**
- **PostgreSQL**
- **Spring Security + JWT** (jjwt 0.12.3)
- **Spring Data JPA / Hibernate**
- **Spring Validation**
- **Lombok**
- **Maven**

## Prérequis

Pour le développement :

- Java 17 ou supérieur
- PostgreSQL 12 ou supérieur
- Maven 3.6 ou supérieur

Pour un déploiement, Docker suffit — voir [Déploiement](#déploiement).

## Configuration

### 1. Base de données

Créer la base de données PostgreSQL (le nom par défaut attendu est `GESCOM_2`) :

```sql
CREATE DATABASE "GESCOM_2";
```

Le schéma est construit par Flyway au démarrage, à partir des migrations de `src/main/resources/db/migration` : il suffit que la base existe et soit vide, l'application joue les scripts elle-même. Hibernate ne fait plus que vérifier la correspondance avec les entités (`spring.jpa.hibernate.ddl-auto=validate`) — une entité modifiée sans migration correspondante fait échouer le démarrage.

Les migrations chargent aussi un jeu de données de départ (110 clients, 180 produits, 12 catégories) et deux comptes :

| Compte | Mot de passe | Rôle |
|---|---|---|
| `admin` | `admin123` | `ADMIN` |
| `caissier1` | `caissier123` | `CAISSIER` |

> ⚠️ Ces comptes ne servent qu'à amorcer une base de développement. **Changez leurs mots de passe dès la première connexion**, et créez les comptes suivants via l'API.

Toute évolution du schéma passe par un nouveau fichier `V15__....sql` : Flyway vérifie la somme de contrôle des scripts déjà joués, en modifier un fait échouer le démarrage suivant.

### 2. Variables d'environnement

L'application lit l'ensemble de ses paramètres sensibles depuis l'environnement. **Toutes les variables ci-dessous doivent être définies en production** ; les valeurs par défaut présentes dans `application.properties` ne servent qu'au démarrage local et ne doivent jamais être utilisées en environnement déployé.

| Variable          | Description                                                        |
|-------------------|--------------------------------------------------------------------|
| `DB_URL`          | URL JDBC PostgreSQL                                                |
| `DB_USERNAME`     | Utilisateur PostgreSQL                                             |
| `DB_PASSWORD`     | Mot de passe PostgreSQL                                            |
| `JWT_SECRET`      | Secret JWT — **64 caractères aléatoires minimum en production**    |
| `JWT_EXPIRATION`  | Durée de validité du token (ms)                                    |
| `CORS_ORIGINS`    | Origines CORS autorisées (séparées par virgule)                    |

Exemple (Linux/macOS) :

```bash
export DB_URL=jdbc:postgresql://localhost:5432/GESCOM_2
export DB_USERNAME=postgres
export DB_PASSWORD=********
export JWT_SECRET=$(openssl rand -hex 64)
export CORS_ORIGINS=https://app.example.com
```

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
pointant sur la machine sont requis — ni Java, ni Node, ni PostgreSQL à installer sur l'hôte.

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

Remplacer `gescom.example.com` par le domaine réel dans `Caddyfile` (bloc de site) et dans
`docker-compose.yml` (variable `CORS_ORIGINS`), puis renseigner l'adresse `email` du bloc global
du `Caddyfile` — c'est là que Let's Encrypt signale les expirations.

```bash
cp .env.example .env
chmod 600 .env          # le fichier porte les secrets de production
$EDITOR .env            # renseigner DB_PASSWORD et JWT_SECRET

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

- `JWT_SECRET` signe l'ensemble des sessions — qui le connaît peut forger un jeton
  d'administrateur sans mot de passe. Le générer (`openssl rand -hex 64`), jamais l'inventer.
  Le changer révoque toutes les sessions en cours, ce qui en fait le geste d'urgence en cas de
  doute.
- Les valeurs par défaut d'`application.properties` **ne font pas échouer le démarrage** si une
  variable manque : l'application démarre avec la valeur de développement. L'absence d'erreur au
  boot ne prouve donc pas que la configuration est complète.

### À faire une fois en ligne

Le déploiement ne couvre pas ces deux points, qui relèvent de l'exploitation :

1. **Changer les mots de passe des comptes amorcés.** Sur une base vierge, les migrations créent
   `admin/admin123` et `caissier1/caissier123` (voir la section Base de données). Tant que ce
   n'est pas fait, l'application est ouverte à qui a lu ce fichier.
2. **Mettre en place les sauvegardes.** Aucune n'est automatique. Par exemple, en tâche cron :

   ```bash
   docker compose exec -T db pg_dump -U gescom gescom | gzip > gescom-$(date +%F).sql.gz
   ```

   Prévoir une rotation et une copie hors de la machine — une sauvegarde qui ne vit que sur le
   serveur sauvegardé ne protège de rien.

### Hôte

N'ouvrir au pare-feu que 22 (SSH), 80 et 443. Le port 80 sert au renouvellement des certificats
et à la redirection vers HTTPS, que Caddy met en place de lui-même.

## Endpoints API

> Toutes les routes sont préfixées par `/api`. Hors `/api/auth/**`, un JWT valide est requis.

### Authentification (`/api/auth`)
- `POST /login` — Connexion (retourne un token JWT)
- `POST /logout` — Déconnexion

### Utilisateurs (`/api/users`) — ADMIN
- `GET /` — Liste des utilisateurs
- `GET /me` — Profil de l'utilisateur connecté
- `PUT /me` — Modifier son profil
- `POST /me/change-password` — Changer son mot de passe
- `GET /{id}` — Détails d'un utilisateur
- `GET /username/{username}` — Recherche par nom d'utilisateur
- `GET /role/{role}` — Filtrer par rôle
- `GET /caissiers` — Liste des caissiers
- `GET /active` — Utilisateurs actifs
- `POST /` — Créer un utilisateur
- `PUT /{id}` — Modifier un utilisateur
- `PATCH /{id}/deactivate` — Désactiver un utilisateur
- `DELETE /{id}` — Supprimer un utilisateur

### Clients (`/api/clients`)
- `GET /` — Liste des clients
- `GET /active` — Clients actifs
- `GET /{id}` — Détails d'un client
- `GET /email/{email}` — Recherche par email
- `GET /type/{type}` — Filtrer par type
- `POST /` — Créer un client
- `PUT /{id}` — Modifier un client
- `PATCH /{id}/deactivate` — Désactiver un client
- `DELETE /{id}` — Supprimer un client
- `GET /export` — Export des clients

### Catégories (`/api/categories`)
- `GET /` — Liste des catégories
- `GET /active` — Catégories actives
- `GET /{id}` — Détails d'une catégorie
- `POST /` — Créer une catégorie
- `PUT /{id}` — Modifier une catégorie
- `PATCH /{id}/toggle-status` — Activer/désactiver
- `DELETE /{id}` — Supprimer une catégorie

### Produits (`/api/products`)
- `GET /` — Liste des produits
- `GET /active` — Produits actifs
- `GET /{id}` — Détails d'un produit
- `GET /code/{code}` — Recherche par code
- `GET /category/{categoryId}` — Produits d'une catégorie
- `GET /low-stock` — Produits en rupture / stock faible
- `GET /{id}/check-stock/{quantity}` — Vérifier disponibilité
- `POST /` — Créer un produit
- `PUT /{id}` — Modifier un produit
- `PATCH /{id}/stock` — Mettre à jour le stock
- `DELETE /{id}` — Supprimer un produit
- `GET /export` — Export des produits
- `POST /import` — Import en lot

### Commandes (`/api/orders`)
- `GET /` — Liste des commandes
- `GET /{id}` — Détails d'une commande
- `GET /number/{orderNumber}` — Recherche par numéro
- `GET /client/{clientId}` — Commandes par client
- `GET /user/{userId}` — Commandes par utilisateur
- `GET /status/{status}` — Filtrer par statut
- `GET /date-range` — Filtrer par plage de dates
- `POST /` — Créer une commande
- `PUT /{id}` — Modifier une commande
- `PATCH /{id}/status` — Changer le statut
- `PATCH /{id}/cancel` — Annuler la commande
- `DELETE /{id}` — Supprimer la commande
- `GET /export` — Export des commandes

### Livraisons (`/api/deliveries`)
- `GET /` — Liste des livraisons
- `GET /{id}` — Détails d'une livraison
- `GET /number/{deliveryNumber}` — Recherche par numéro
- `GET /order/{orderId}` — Livraisons d'une commande
- `GET /status/{status}` — Filtrer par statut
- `GET /date-range` — Filtrer par plage de dates
- `POST /` — Créer une livraison
- `PUT /{id}` — Modifier une livraison
- `PATCH /{id}/status` — Changer le statut
- `PATCH /{id}/mark-delivered` — Marquer comme livrée
- `POST /{id}/create-invoice` — Générer la facture associée
- `DELETE /{id}` — Supprimer la livraison
- `GET /export` — Export des livraisons

### Factures (`/api/invoices`)
- `GET /` — Liste des factures
- `GET /{id}` — Détails d'une facture
- `GET /number/{invoiceNumber}` — Recherche par numéro
- `GET /order/{orderId}` — Factures d'une commande
- `GET /status/{status}` — Filtrer par statut
- `GET /date-range` — Filtrer par plage de dates
- `GET /overdue` — Factures en retard
- `POST /` — Créer une facture
- `PATCH /{id}/payment` — Enregistrer un paiement
- `PATCH /{id}/cancel` — Annuler une facture
- `DELETE /{id}` — Supprimer une facture

### Stock (`/api/stock`)
- `GET /movements` — Liste des mouvements
- `GET /movements/{id}` — Détails d'un mouvement
- `GET /movements/product/{productId}` — Mouvements d'un produit
- `GET /movements/type/{type}` — Filtrer par type de mouvement
- `GET /movements/date-range` — Filtrer par plage de dates
- `POST /add` — Entrée de stock
- `POST /remove` — Sortie de stock
- `POST /adjust` — Ajustement de stock
- `POST /damage` — Déclarer une casse
- `GET /low-stock` — Produits en stock faible
- `GET /out-of-stock` — Produits en rupture
- `GET /statistics` — Statistiques de stock
- `DELETE /movements/{id}` — Supprimer un mouvement
- `GET /export` — Export des mouvements

### Journal d'activité (`/api/activities`)
- `GET /` — Liste des activités
- `GET /{id}` — Détails d'une activité
- `GET /user/{userId}` — Activités d'un utilisateur
- `GET /action/{actionType}` — Filtrer par type d'action
- `GET /entity/{entity}` — Filtrer par entité
- `GET /date-range` — Filtrer par plage de dates
- `GET /caissiers` — Activités des caissiers
- `POST /` — Créer une entrée
- `DELETE /{id}` — Supprimer une entrée

### Tableau de bord (`/api/dashboard`)
- `GET /stats` — Indicateurs clés
- `GET /recent-orders` — Dernières commandes
- `GET /top-products` — Meilleures ventes
- `GET /overview` — Vue d'ensemble

### Paramètres (`/api/settings`)
- `GET /` — Lire les paramètres applicatifs
- `PUT /` — Mettre à jour les paramètres

## Authentification JWT

Toutes les requêtes (sauf `/api/auth/**`) nécessitent un token JWT dans l'en-tête :

```
Authorization: Bearer <votre_token_jwt>
```

## Rôles utilisateurs

- **ADMIN** — Accès complet à toutes les fonctionnalités
- **CAISSIER** — Accès aux opérations courantes (clients, produits, commandes, livraisons, factures)

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
│   ├── exception/     # Exceptions métier et handler global
│   ├── security/      # Filtres et configuration JWT
│   └── config/        # Configuration Spring
├── src/main/resources/
│   ├── application.properties
│   └── i18n/          # Messages serveur (fr / en / nl)
├── src/test/java/     # Tests JUnit
├── FRONT/             # Client React + Vite + Tailwind (voir FRONT/README.md)
├── Dockerfile         # Build des deux applications, cibles `api` et `web`
├── docker-compose.yml # Déploiement : web (Caddy) + api + db
├── Caddyfile          # TLS, service du SPA, relais /api
├── .env.example       # Modèle de configuration ; le .env réel n'est pas versionné
└── pom.xml
```
