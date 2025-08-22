# Système de Paiement Externe GESCOM

## Vue d'ensemble

Le système de paiement externe de GESCOM permet aux clients de régler leurs factures en ligne sans créer de compte utilisateur. Il est conçu avec des normes de sécurité élevées et une conformité PCI-DSS complète.

## Fonctionnalités Principales

### 🎯 Fonctionnalités Client
- ✅ Recherche de facture par numéro
- ✅ Sélection du mode de paiement (Visa, MasterCard)
- ✅ Paiement sécurisé via Stripe
- ✅ Confirmation par email
- ✅ Interface responsive et accessible

### 🔒 Sécurité et Conformité
- ✅ Conformité PCI-DSS
- ✅ Chiffrement SSL/TLS
- ✅ Tokenisation des données bancaires
- ✅ Protection contre les attaques (rate limiting, IP blocking)
- ✅ Audit et logging complets
- ✅ Validation automatique des webhooks

### 📊 Administration et Monitoring
- ✅ Tableau de bord des paiements
- ✅ Rapports de sécurité automatiques
- ✅ Gestion des IP suspectes
- ✅ Statistiques en temps réel
- ✅ Nettoyage automatique des données expirées

## Architecture Technique

### Composants Principaux

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│     Client      │    │   Application   │    │     Stripe      │
│   (Frontend)    │◄──►│   GESCOM        │◄──►│   (Gateway)     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌─────────────────┐
                       │   Base de       │
                       │   Données       │
                       └─────────────────┘
```

### Technologies Utilisées

- **Backend**: Spring Boot, Spring Security
- **Base de données**: MySQL avec migration Flyway
- **Cache/Session**: Redis
- **Passerelle de paiement**: Stripe
- **Templates**: Thymeleaf
- **Frontend**: Bootstrap 5, JavaScript ES6
- **Monitoring**: Actuator, Micrometer

## Structure des Données

### Tables Principales

#### `external_payments`
Stocke les informations de paiement (AUCUNE DONNÉE BANCAIRE SENSIBLE)

```sql
CREATE TABLE external_payments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    gateway_provider VARCHAR(50) NOT NULL,
    gateway_transaction_id VARCHAR(255),
    security_token VARCHAR(255) UNIQUE,
    client_ip VARCHAR(45),
    expires_at TIMESTAMP,
    completed_at TIMESTAMP,
    -- ... autres champs
);
```

#### `payment_security_logs`
Journal de sécurité pour tous les événements de paiement

```sql
CREATE TABLE payment_security_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    client_ip VARCHAR(45) NOT NULL,
    event_details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### `suspicious_payment_attempts`
Suivi des tentatives suspectes par IP

```sql
CREATE TABLE suspicious_payment_attempts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    client_ip VARCHAR(45) NOT NULL UNIQUE,
    failed_attempts INT NOT NULL DEFAULT 0,
    blocked_until TIMESTAMP,
    block_reason TEXT
);
```

## Flux de Paiement

### 1. Recherche de Facture

```
Client saisit le numéro de facture
        ↓
Validation côté serveur
        ↓
Vérification de l'éligibilité au paiement
        ↓
Redirection vers la sélection du mode de paiement
```

### 2. Processus de Paiement

```
Sélection du mode de paiement + saisie des informations
        ↓
Validation et création du token de sécurité
        ↓
Création de la session Stripe
        ↓
Redirection vers Stripe Checkout
        ↓
Traitement du paiement par Stripe
        ↓
Webhook de confirmation
        ↓
Mise à jour automatique de la facture
        ↓
Envoi des notifications par email
```

## Configuration

### Variables d'Environnement

```bash
# Stripe Configuration
STRIPE_SECRET_KEY=sk_live_your_secret_key
STRIPE_PUBLISHABLE_KEY=pk_live_your_publishable_key
STRIPE_WEBHOOK_SECRET=whsec_your_webhook_secret

# Application Configuration
APP_BASE_URL=https://votre-domaine.com
PAYMENT_ENABLED=true

# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password

# Email Configuration
SMTP_HOST=smtp.gmail.com
SMTP_USERNAME=your_email@domain.com
SMTP_PASSWORD=your_app_password
ADMIN_EMAIL=admin@votre-domaine.com
```

### Configuration SSL/HTTPS

**OBLIGATOIRE pour la production**

```nginx
server {
    listen 443 ssl http2;
    server_name votre-domaine.com;
    
    ssl_certificate /path/to/certificate.crt;
    ssl_certificate_key /path/to/private.key;
    
    # Configuration SSL sécurisée
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES128-GCM-SHA256:ECDHE-RSA-AES256-GCM-SHA384;
    ssl_prefer_server_ciphers off;
    
    # Headers de sécurité
    add_header Strict-Transport-Security "max-age=63072000" always;
    add_header X-Frame-Options DENY;
    add_header X-Content-Type-Options nosniff;
    
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## Sécurité

### Mesures de Protection

1. **Protection des Données**
   - Aucune donnée bancaire stockée
   - Tokenisation complète par Stripe
   - Chiffrement en transit et au repos

2. **Protection des Attaques**
   - Rate limiting par IP
   - Détection des patterns suspects
   - Blocage automatique des IP malveillantes
   - Validation des webhooks par signature

3. **Audit et Conformité**
   - Log de toutes les tentatives
   - Rapports de sécurité automatiques
   - Nettoyage automatique des données sensibles
   - Conformité RGPD

### Configuration de Sécurité

```yaml
security:
  payment:
    max-attempts-per-ip: 5
    rate-limit-window: 3600  # 1 heure
    suspicious-threshold: 10
    cors:
      allowed-origins:
        - https://votre-domaine.com
        - https://checkout.stripe.com
```

## Monitoring et Maintenance

### Métriques Surveillées

- Taux de succès des paiements
- Nombre de tentatives suspectes
- Performance des API
- Disponibilité du service

### Tâches Automatiques

```cron
# Nettoyage des paiements expirés (toutes les heures)
0 0 * * * * - CleanupExpiredPayments

# Rapport de sécurité quotidien (8h)
0 0 8 * * * - SecurityReport

# Nettoyage des logs anciens (dimanche 2h)
0 0 2 * * SUN - CleanupOldLogs
```

### Alertes Automatiques

- IP avec plus de 10 tentatives échouées
- Taux de succès < 90%
- Détection d'activité suspecte
- Erreurs système critiques

## API Endpoints

### Endpoints Publics (sans authentification)

| Endpoint | Méthode | Description |
|----------|---------|-------------|
| `/payment` | GET | Page de recherche de facture |
| `/payment/search` | POST | Recherche d'une facture |
| `/payment/select-method` | GET | Sélection du mode de paiement |
| `/payment/initiate` | POST | Initiation du paiement |
| `/payment/stripe` | GET | Redirection vers Stripe |
| `/payment/success` | GET | Page de confirmation |
| `/payment/cancel` | GET | Page d'annulation |
| `/payment/webhook/stripe` | POST | Webhook Stripe |
| `/payment/status/{token}` | GET | API de statut |

### Paramètres de Sécurité

Tous les endpoints publics sont protégés par :
- Rate limiting
- Validation CSRF (sauf webhooks)
- Validation des tokens
- Logging des tentatives

## Tests et Validation

### Tests de Sécurité

```bash
# Test de rate limiting
for i in {1..10}; do
  curl -X POST https://votre-domaine.com/payment/search
done

# Test de validation des tokens
curl -X GET https://votre-domaine.com/payment/status/invalid_token

# Test des webhooks
curl -X POST https://votre-domaine.com/payment/webhook/stripe \
  -H "Stripe-Signature: invalid" \
  -d "test_payload"
```

### Configuration de Test Stripe

```yaml
stripe:
  api:
    secret-key: sk_test_your_test_key
    publishable-key: pk_test_your_test_key
  webhook:
    secret: whsec_test_your_webhook_secret
```

**Cartes de test Stripe :**
- Visa réussie: `4242424242424242`
- Visa échouée: `4000000000000002`
- MasterCard réussie: `5555555555554444`

## Déploiement

### Étapes de Déploiement

1. **Préparation**
   ```bash
   # Compilation
   ./mvnw clean package -DskipTests
   
   # Vérification des configurations
   ./mvnw spring-boot:run --spring.profiles.active=production
   ```

2. **Base de données**
   ```sql
   -- Exécution des migrations
   ./mvnw flyway:migrate
   
   -- Vérification des tables
   SHOW TABLES LIKE '%payment%';
   ```

3. **Configuration SSL**
   - Certificat SSL valide
   - Configuration HTTPS forcée
   - Headers de sécurité

4. **Variables d'environnement**
   - Clés Stripe de production
   - Configuration Redis
   - Configuration SMTP

### Checklist de Mise en Production

- [ ] Certificat SSL installé et valide
- [ ] Variables d'environnement configurées
- [ ] Base de données migrée
- [ ] Redis opérationnel
- [ ] Configuration SMTP testée
- [ ] Webhooks Stripe configurés
- [ ] Monitoring activé
- [ ] Sauvegardes configurées
- [ ] Tests de paiement effectués
- [ ] Documentation équipe fournie

## Support et Maintenance

### Contacts Techniques

- **Développement**: dev@votre-domaine.com
- **Sécurité**: security@votre-domaine.com
- **Infrastructure**: ops@votre-domaine.com

### Escalation des Incidents

1. **Niveau 1**: Problème mineur (< 2h)
   - Redémarrage de service
   - Vérification des logs

2. **Niveau 2**: Problème majeur (< 30min)
   - Panne de paiement
   - Problème de sécurité

3. **Niveau 3**: Incident critique (< 15min)
   - Faille de sécurité
   - Perte de données
   - Service indisponible

### Logs Importants

```bash
# Logs de paiement
tail -f /var/log/gescom/payments.log

# Logs de sécurité
tail -f /var/log/gescom/security.log

# Logs d'application
tail -f /var/log/gescom/application.log
```

## Évolutions Futures

### Fonctionnalités Prévues

- [ ] Support PayPal
- [ ] Paiement par virement SEPA
- [ ] Paiement en plusieurs fois
- [ ] API mobile
- [ ] Paiement par QR Code
- [ ] Support Apple Pay / Google Pay

### Améliorations Techniques

- [ ] Cache Redis distribué
- [ ] Monitoring avancé avec Prometheus
- [ ] Tests automatisés de sécurité
- [ ] Déploiement Docker
- [ ] CI/CD avec GitLab

---

**Important**: Ce système traite des données financières sensibles. Toute modification doit être validée par l'équipe sécurité et testée en environnement de préproduction avant déploiement.

**Contact Support**: support@gescom.fr | 01 23 45 67 89