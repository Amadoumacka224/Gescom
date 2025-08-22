# Guide - Fonctionnalités PDF et Email des Factures

## Vue d'ensemble

Les fonctionnalités de téléchargement PDF et d'envoi par email des factures sont maintenant opérationnelles dans GESCOM.

## Fonctionnalités disponibles

### 1. Téléchargement PDF
- **Accès** : Bouton "Télécharger PDF" sur la page détails d'une facture
- **URL** : `GET /invoices/{id}/pdf`
- **Fonctionnalités** :
  - Génération automatique du PDF avec toutes les informations de la facture
  - Design professionnel avec logo, en-tête et mise en forme
  - Informations client, articles détaillés, totaux et taxes
  - Conditions de paiement et notes
  - Informations de paiement si disponibles

### 2. Envoi par Email
- **Accès** : Bouton "Envoyer par email" sur la page détails d'une facture
- **URL** : `POST /invoices/{id}/send-email`
- **Fonctionnalités** :
  - Email HTML professionnel avec détails de la facture
  - Pièce jointe PDF optionnelle
  - Mise à jour automatique du statut de la facture
  - Traçabilité de l'envoi (date et heure)

### 3. Relances de Paiement
- **Accès** : Bouton "Relance" (visible selon le statut de la facture)
- **URL** : `POST /invoices/{id}/remind`
- **Conditions d'affichage** :
  - Statut SENT, OVERDUE ou PARTIAL
  - Email client disponible
- **Fonctionnalités** :
  - Email de relance spécialisé
  - Calcul automatique des jours de retard
  - Pièce jointe PDF incluse
  - Ton adapté selon la situation

## Configuration Email

### Configuration de base (dans application.properties)

```properties
# Pour un serveur SMTP local (développement)
spring.mail.host=localhost
spring.mail.port=1025
spring.mail.properties.mail.smtp.auth=false
spring.mail.properties.mail.smtp.starttls.enable=false

# Configuration de l'application
app.mail.from=noreply@gescom.com
app.company.name=Votre Entreprise
```

### Configuration pour Gmail

```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=votre-email@gmail.com
spring.mail.password=votre-mot-de-passe-app
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

### Configuration pour Outlook/Office365

```properties
spring.mail.host=smtp.office365.com
spring.mail.port=587
spring.mail.username=votre-email@outlook.com
spring.mail.password=votre-mot-de-passe
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

## Interface Utilisateur

### Boutons d'Action
La page détails d'une facture contient maintenant :

1. **Télécharger PDF** (bleu) - Toujours visible
2. **Envoyer par email** (info) - Toujours visible
3. **Relance** (warning) - Visible selon conditions
4. **Encaisser** (success) - Si facture non payée/annulée
5. **Modifier** (secondary) - Si facture en brouillon

### Modals Interactives

#### Modal d'Envoi Email
- Pré-remplissage avec l'email du client
- Option d'attacher le PDF
- Aperçu du contenu de l'email
- Mise à jour automatique du statut

#### Modal de Relance
- Affichage des informations de retard
- Détails du montant restant dû
- Vérification de l'email client
- Aperçu du contenu de la relance

## Services Implémentés

### InvoicePdfService
- **Localisation** : `com.gescom.service.InvoicePdfService`
- **Dépendance** : iText PDF (version 8.0.0)
- **Fonctionnalités** :
  - Génération PDF complète
  - Mise en forme professionnelle
  - Support des devises et pourcentages
  - Gestion des remises et frais de port

### EmailService
- **Localisation** : `com.gescom.service.EmailService`
- **Dépendance** : Spring Mail
- **Fonctionnalités** :
  - Templates HTML responsives
  - Gestion des pièces jointes
  - Emails de facture et de relance
  - Logs détaillés

## Structure des Emails

### Email de Facture
- **Objet** : "Facture [NUMERO] - [NOM_ENTREPRISE]"
- **Contenu** :
  - Salutation personnalisée
  - Détails de la facture
  - Instructions de paiement
  - Informations de contact

### Email de Relance
- **Objet** : "Relance - Facture [NUMERO] - [NOM_ENTREPRISE]"
- **Contenu** :
  - Alerte de retard
  - Rappel des détails
  - Demande d'action urgente
  - Proposition de contact

## Gestion des Erreurs

### Erreurs PDF
- Log des erreurs de génération
- Retour HTTP 500 en cas d'échec
- Messages d'erreur explicites

### Erreurs Email
- Validation des adresses email
- Gestion des timeouts SMTP
- Messages d'erreur utilisateur
- Rollback des statuts en cas d'échec

## Logs et Traçabilité

### Logs Applicatifs
- Génération PDF : INFO level
- Envoi email : INFO level
- Erreurs : ERROR level avec stack trace

### Traçabilité Base de Données
- `email_sent` : Boolean (facture envoyée)
- `email_sent_date` : DateTime (date d'envoi)
- `status` : Mise à jour automatique

## Tests et Développement

### Serveur SMTP de Test
Pour le développement, utilisez MailHog :

```bash
# Installation avec Docker
docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog

# Accès interface web : http://localhost:8025
```

### Configuration Test
```properties
spring.mail.host=localhost
spring.mail.port=1025
spring.mail.test-connection=false
```

## Dépendances Ajoutées

```xml
<!-- PDF Generation -->
<dependency>
    <groupId>com.itextpdf</groupId>
    <artifactId>itext7-core</artifactId>
    <version>8.0.0</version>
    <type>pom</type>
</dependency>

<!-- Email -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

## Sécurité

### Validation des Données
- Validation des IDs de facture
- Validation des adresses email
- Protection contre l'injection

### Contrôle d'Accès
- Vérification des permissions utilisateur
- Accès seulement aux factures autorisées
- Logs des actions utilisateur

## Performances

### Optimisations PDF
- Génération en mémoire (ByteArrayOutputStream)
- Polices intégrées
- Images optimisées

### Optimisations Email
- Templates pré-compilés
- Connexions SMTP réutilisées
- Timeouts configurables

## Maintenance

### Monitoring
- Logs des erreurs SMTP
- Métriques de génération PDF
- Taux de réussite des envois

### Sauvegarde
- Les PDF ne sont pas stockés (génération à la demande)
- Sauvegarde des configurations email
- Historique des envois en base

## Support

### Problèmes Courants

1. **PDF ne se génère pas**
   - Vérifier les logs application
   - Vérifier les données de la facture
   - Contrôler les dépendances iText

2. **Email ne s'envoie pas**
   - Vérifier la configuration SMTP
   - Tester la connexion réseau
   - Contrôler les credentials

3. **Interface ne répond pas**
   - Vérifier les logs JavaScript console
   - Contrôler la configuration Bootstrap
   - Valider les modals

### Logs Utiles
```bash
# Logs application
tail -f logs/application.log

# Filtrer les logs PDF/Email
grep -E "(PDF|Email)" logs/application.log

# Erreurs uniquement
grep "ERROR" logs/application.log
```

---

## Résumé des URLs

| Action | Méthode | URL | Description |
|--------|---------|-----|-------------|
| Télécharger PDF | GET | `/invoices/{id}/pdf` | Génère et télécharge le PDF |
| Envoyer Email | POST | `/invoices/{id}/send-email` | Envoie la facture par email |
| Envoyer Relance | POST | `/invoices/{id}/remind` | Envoie une relance de paiement |

Les fonctionnalités sont maintenant prêtes à l'utilisation !