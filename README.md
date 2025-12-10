# GESCOM - Backend API

Application de gestion commerciale développée avec Spring Boot.

## Technologies

- **Spring Boot 3.2.0**
- **Java 17**
- **PostgreSQL**
- **Spring Security + JWT**
- **Spring Data JPA**
- **Maven**

## Prérequis

- Java 17 ou supérieur
- PostgreSQL 12 ou supérieur
- Maven 3.6 ou supérieur

## Configuration de la base de données

1. Créer une base de données PostgreSQL :
```sql
CREATE DATABASE gescom_db;
```

2. Mettre à jour les informations de connexion dans `src/main/resources/application.properties` :
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/gescom_db
spring.datasource.username=votre_username
spring.datasource.password=votre_password
```

## Installation et démarrage

1. Cloner le projet
2. Installer les dépendances :
```bash
mvn clean install
```

3. Lancer l'application :
```bash
mvn spring-boot:run
```

L'API sera accessible sur `http://localhost:8080`

## Endpoints API

### Authentification
- `POST /api/auth/login` - Connexion
- `POST /api/auth/logout` - Déconnexion

### Utilisateurs (ADMIN uniquement)
- `GET /api/users` - Liste des utilisateurs
- `GET /api/users/{id}` - Détails d'un utilisateur
- `POST /api/users` - Créer un utilisateur
- `PUT /api/users/{id}` - Modifier un utilisateur
- `DELETE /api/users/{id}` - Supprimer un utilisateur
- `PATCH /api/users/{id}/deactivate` - Désactiver un utilisateur

### Clients
- `GET /api/clients` - Liste des clients
- `GET /api/clients/active` - Clients actifs
- `GET /api/clients/{id}` - Détails d'un client
- `POST /api/clients` - Créer un client
- `PUT /api/clients/{id}` - Modifier un client
- `DELETE /api/clients/{id}` - Supprimer un client

### Produits
- `GET /api/products` - Liste des produits
- `GET /api/products/active` - Produits actifs
- `GET /api/products/{id}` - Détails d'un produit
- `GET /api/products/low-stock` - Produits en rupture
- `POST /api/products` - Créer un produit
- `PUT /api/products/{id}` - Modifier un produit
- `PATCH /api/products/{id}/stock` - Mettre à jour le stock
- `DELETE /api/products/{id}` - Supprimer un produit

### Commandes
- `GET /api/orders` - Liste des commandes
- `GET /api/orders/{id}` - Détails d'une commande
- `GET /api/orders/client/{clientId}` - Commandes par client
- `POST /api/orders` - Créer une commande
- `PATCH /api/orders/{id}/status` - Changer le statut
- `PATCH /api/orders/{id}/cancel` - Annuler une commande

### Livraisons
- `GET /api/deliveries` - Liste des livraisons
- `GET /api/deliveries/{id}` - Détails d'une livraison
- `POST /api/deliveries` - Créer une livraison
- `PATCH /api/deliveries/{id}/mark-delivered` - Marquer comme livrée

### Factures
- `GET /api/invoices` - Liste des factures
- `GET /api/invoices/{id}` - Détails d'une facture
- `GET /api/invoices/overdue` - Factures en retard
- `POST /api/invoices` - Créer une facture
- `PATCH /api/invoices/{id}/payment` - Enregistrer un paiement
- `PATCH /api/invoices/{id}/cancel` - Annuler une facture

## Authentification JWT

Toutes les requêtes (sauf `/api/auth/**`) nécessitent un token JWT dans le header :
```
Authorization: Bearer <votre_token_jwt>
```

## Rôles utilisateurs

- **ADMIN** : Accès complet à toutes les fonctionnalités
- **CAISSIER** : Accès aux opérations courantes (clients, produits, commandes, livraisons, factures)

## Support multi-langue

L'application supporte :
- Français (fr)
- Anglais (en)

Les fichiers de messages se trouvent dans `src/main/resources/i18n/`
