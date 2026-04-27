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

- Java 17 ou supérieur
- PostgreSQL 12 ou supérieur
- Maven 3.6 ou supérieur

## Configuration

### 1. Base de données

Créer la base de données PostgreSQL (le nom par défaut attendu est `GESCOM_2`) :

```sql
CREATE DATABASE "GESCOM_2";
```

(Optionnel — **développement uniquement**) Initialiser des comptes de test :

```bash
psql -U postgres -d GESCOM_2 -f init-admin.sql
```

> ⚠️ Le script `init-admin.sql` crée des comptes avec des mots de passe par défaut destinés au développement local. **Ne jamais l'exécuter tel quel en production** : changez les mots de passe immédiatement après la première connexion, ou créez vos comptes via l'API.

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

```
BACK/
├── src/main/java/com/gescom/backend/
│   ├── controller/    # Contrôleurs REST
│   ├── service/       # Logique métier
│   ├── repository/    # Accès aux données (JPA)
│   ├── entity/        # Entités JPA
│   ├── dto/           # DTOs Request / Response
│   ├── security/      # Filtres et configuration JWT
│   └── config/        # Configuration Spring
├── src/main/resources/
│   ├── application.properties
│   └── i18n/          # Fichiers de traduction
├── init-admin.sql     # Script d'initialisation admin
└── pom.xml
```
