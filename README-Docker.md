# 🐳 Déploiement Docker - GESCOM

Ce guide vous explique comment déployer l'application GESCOM avec Docker et Docker Compose.

## 📋 Prérequis

- **Docker Desktop** (Windows/Mac) ou **Docker Engine** (Linux)
- **Docker Compose** (inclus avec Docker Desktop)
- Au minimum **4 GB de RAM** disponible pour Docker
- **Ports 8080, 5432, et 5050** disponibles

## 🚀 Déploiement rapide

### Option 1: Script automatique (Windows)
```cmd
# Déploiement standard
deploy.bat

# Déploiement avec nettoyage complet
deploy.bat --clean-images

# Voir l'aide
deploy.bat --help
```

### Option 2: Script automatique (Linux/Mac)
```bash
# Rendre le script exécutable
chmod +x deploy.sh

# Déploiement standard
./deploy.sh

# Déploiement avec nettoyage complet
./deploy.sh --clean-images

# Voir l'aide
./deploy.sh --help
```

### Option 3: Commandes manuelles
```bash
# 1. Construire et démarrer tous les services
docker-compose up -d --build

# 2. Vérifier que les services sont démarrés
docker-compose ps

# 3. Voir les logs
docker-compose logs -f
```

## 📊 Services déployés

| Service | URL | Description |
|---------|-----|-------------|
| **Application GESCOM** | http://localhost:8080 | Application principale |
| **Base de données** | localhost:5432 | PostgreSQL |
| **PgAdmin** | http://localhost:5050 | Interface d'administration DB |
| **Health Check** | http://localhost:8080/actuator/health | Monitoring |

## 🗄️ Configuration de la base de données

### Connexion PostgreSQL
- **Host:** localhost
- **Port:** 5432
- **Database:** gescom
- **Username:** gescom_user
- **Password:** gescom_password

### PgAdmin (Interface web)
- **URL:** http://localhost:5050
- **Email:** admin@gescom.local
- **Password:** admin123

Pour ajouter le serveur PostgreSQL dans PgAdmin:
1. Cliquez sur "Add New Server"
2. **Name:** GESCOM DB
3. **Host:** postgres (nom du service Docker)
4. **Port:** 5432
5. **Database:** gescom
6. **Username:** gescom_user
7. **Password:** gescom_password

## 🔧 Commandes utiles

### Gestion des services
```bash
# Démarrer tous les services
docker-compose up -d

# Arrêter tous les services
docker-compose down

# Redémarrer un service spécifique
docker-compose restart app

# Voir les logs en temps réel
docker-compose logs -f app
docker-compose logs -f postgres

# Voir l'état des services
docker-compose ps

# Exécuter une commande dans un conteneur
docker-compose exec app bash
docker-compose exec postgres psql -U gescom_user -d gescom
```

### Maintenance
```bash
# Nettoyer complètement (ATTENTION: supprime les données)
docker-compose down -v
docker system prune -a

# Sauvegarder la base de données
docker-compose exec postgres pg_dump -U gescom_user gescom > backup.sql

# Restaurer la base de données
docker-compose exec -T postgres psql -U gescom_user gescom < backup.sql

# Voir l'utilisation des ressources
docker stats
```

## 📁 Structure des fichiers Docker

```
GESCOM/
├── Dockerfile                 # Image de l'application
├── docker-compose.yml        # Orchestration des services
├── .dockerignore             # Fichiers à ignorer lors du build
├── deploy.sh                 # Script de déploiement (Linux/Mac)
├── deploy.bat                # Script de déploiement (Windows)
└── docker/
    ├── init-db.sql           # Script d'initialisation de la DB
    └── nginx.conf            # Configuration Nginx (production)
```

## ⚙️ Configuration avancée

### Variables d'environnement
Créez un fichier `.env` dans le répertoire racine pour personnaliser:

```env
# Base de données
POSTGRES_DB=gescom
POSTGRES_USER=gescom_user
POSTGRES_PASSWORD=votre_mot_de_passe_securise

# Application
SPRING_PROFILES_ACTIVE=docker
JAVA_OPTS=-Xmx1g -Xms512m

# Email (optionnel)
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=votre@email.com
SMTP_PASSWORD=votre_mot_de_passe
```

### Production avec Nginx
Pour un déploiement en production avec reverse proxy:

```bash
# Démarrer avec Nginx
docker-compose --profile production up -d

# L'application sera accessible sur le port 80/443
```

### Personnaliser les ports
Modifiez le `docker-compose.yml` pour changer les ports:

```yaml
services:
  app:
    ports:
      - "8081:8080"  # Application sur le port 8081
  postgres:
    ports:
      - "5433:5432"  # PostgreSQL sur le port 5433
```

## 🛠️ Dépannage

### L'application ne démarre pas
```bash
# Vérifier les logs
docker-compose logs app

# Vérifier si le port est occupé
netstat -an | findstr 8080  # Windows
lsof -i :8080               # Linux/Mac

# Redémarrer avec reconstruction
docker-compose up -d --build --force-recreate
```

### Problème de base de données
```bash
# Vérifier PostgreSQL
docker-compose logs postgres

# Se connecter à la base
docker-compose exec postgres psql -U gescom_user -d gescom

# Réinitialiser la base (ATTENTION: perte de données)
docker-compose down -v
docker-compose up -d
```

### Problème de mémoire
```bash
# Vérifier l'utilisation
docker stats

# Augmenter la mémoire de Docker Desktop dans les paramètres
# ou modifier JAVA_OPTS dans docker-compose.yml
```

## 📈 Monitoring et maintenance

### Health Checks
- **Application:** http://localhost:8080/actuator/health
- **Base de données:** Vérifiée automatiquement par Docker

### Logs
- **Application:** `docker-compose logs -f app`
- **Base de données:** `docker-compose logs -f postgres`
- **Fichiers de logs:** `./logs/gescom.log`

### Sauvegardes automatiques
Ajoutez cette tâche cron pour des sauvegardes quotidiennes:

```bash
# Crontab entry (Linux/Mac)
0 2 * * * cd /path/to/GESCOM && docker-compose exec -T postgres pg_dump -U gescom_user gescom > backup_$(date +\%Y\%m\%d).sql
```

## 🆘 Support

En cas de problème:

1. Vérifiez les logs: `docker-compose logs -f`
2. Vérifiez l'état des services: `docker-compose ps`
3. Redémarrez les services: `docker-compose restart`
4. En dernier recours: `docker-compose down && docker-compose up -d --build`

---

**Note:** Ce guide suppose une utilisation en développement/test. Pour un déploiement en production, configurez des mots de passe sécurisés et utilisez HTTPS.