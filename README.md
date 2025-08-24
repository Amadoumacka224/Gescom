# Application de Gestion Commerciale

Une application web complète de gestion commerciale développée avec Spring Boot, Thymeleaf et postgreSQL.

## 🚀 Fonctionnalités

### 🔐 Authentification & Sécurité
- Connexion/déconnexion sécurisée avec Spring Security
- Encodage des mots de passe avec BCrypt
- Gestion des rôles (Admin, Commercial)
- Session management et protection CSRF

### 👤 Gestion des Utilisateurs
- CRUD complet des utilisateurs
- Affectation des rôles et permissions
- Journalisation des connexions et actions critiques
- Verrouillage de compte et gestion des tentatives échouées

### 🧾 Clients et Fournisseurs
- Fiches complètes avec coordonnées et statut
- Historique des transactions
- Recherche multicritères avancée
- Gestion des types de clients (Particulier/Entreprise)

### 📦 Gestion des Produits
- CRUD produits avec catégorisation
- Gestion des stocks (quantité, seuils, alertes)
- Suivi des mouvements de stock
- Import/export CSV

### 💰 Module Ventes
- Devis, bons de commande, bons de livraison, factures
- Suivi des règlements et échéances
- Génération PDF des documents commerciaux
- Calculs automatiques de TVA et totaux

### 🛒 Module Achats
- Commandes fournisseurs et réceptions
- Facturation fournisseur
- Lien automatique avec la gestion de stock


### 📊 Statistiques & Tableaux de Bord
- Vue synthétique du chiffre d'affaires
- Top des ventes et produits manquants
- Graphiques interactifs avec Chart.js
- Rapports détaillés par période

