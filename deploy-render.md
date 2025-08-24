# Guide de déploiement sur Render

## Prérequis
- Compte Render créé
- Base de données PostgreSQL créée sur Render
- Repository GitHub connecté à Render

## Configuration de la base de données

Vos informations de base de données Render :
- **Hostname**: dpg-d2lha1fdiees73c1k530-a.oregon-postgres.render.com
- **Port**: 5432
- **Database**: gescom_k7fn
- **Username**: gescom_k7fn_user
- **Password**: RQBmTXidsNaUtgD2JQEqYXUcDVZ5aTsf
- **Internal URL**: postgresql://gescom_k7fn_user:RQBmTXidsNaUtgD2JQEqYXUcDVZ5aTsf@dpg-d2lha1fdiees73c1k530-a/gescom_k7fn
- **External URL**: postgresql://gescom_k7fn_user:RQBmTXidsNaUtgD2JQEqYXUcDVZ5aTsf@dpg-d2lha1fdiees73c1k530-a.oregon-postgres.render.com/gescom_k7fn

## Étapes de déploiement

### 1. Configuration du service Web sur Render

1. **Connecter votre repository** :
   - Allez dans Render Dashboard
   - Cliquez sur "New +" > "Web Service"
   - Connectez votre repository GitHub

2. **Configuration du service** :
   - **Name**: `gescom-app`
   - **Environment**: `Docker`
   - **Region**: `Oregon` (ou votre région préférée)
   - **Branch**: `main` (ou votre branche principale)
   - **Dockerfile Path**: `./Dockerfile`

3. **Variables d'environnement à configurer** :
   ```
   SPRING_PROFILES_ACTIVE=render
   DATABASE_URL=postgresql://gescom_k7fn_user:RQBmTXidsNaUtgD2JQEqYXUcDVZ5aTsf@dpg-d2lha1fdiees73c1k530-a.oregon-postgres.render.com/gescom_k7fn
   DB_USERNAME=gescom_k7fn_user
   DB_PASSWORD=RQBmTXidsNaUtgD2JQEqYXUcDVZ5aTsf
   JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
   ```

4. **Configuration avancée** :
   - **Health Check Path**: `/actuator/health`
   - **Build Command**: *(laisser vide - géré par Dockerfile)*
   - **Start Command**: *(laisser vide - géré par Dockerfile)*

### 2. Plan recommandé
- **Pour les tests**: Plan `Starter` (7$/mois)
- **Pour la production**: Plan `Standard` (25$/mois)

### 3. Configuration de la base de données
Votre base de données PostgreSQL est déjà créée avec ces paramètres :
- **Plan**: Free (ou Starter selon vos besoins)
- **Region**: Oregon
- **Database Name**: gescom_k7fn

### 4. Test local avec Docker

**Option 1 - Build complet (recommandé pour Render)** :
```bash
# Build de l'image avec Maven intégré
docker build -t gescom-app .
```

**Option 2 - Build avec JAR pré-compilé (plus rapide pour les tests locaux)** :
```bash
# Compiler l'application localement
mvn clean package -DskipTests

# Build avec Dockerfile simple
docker build -f Dockerfile.simple -t gescom-app .
```

**Option 3 - Utiliser Maven Wrapper** :
```bash
# Si vous avez des problèmes avec Maven
docker build -f Dockerfile.alternative -t gescom-app .
```

**Test local** :
```bash
# Test local avec les variables d'environnement Render
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=render \
  -e DATABASE_URL="postgresql://gescom_k7fn_user:RQBmTXidsNaUtgD2JQEqYXUcDVZ5aTsf@dpg-d2lha1fdiees73c1k530-a.oregon-postgres.render.com/gescom_k7fn" \
  -e DB_USERNAME=gescom_k7fn_user \
  -e DB_PASSWORD=RQBmTXidsNaUtgD2JQEqYXUcDVZ5aTsf \
  gescom-app
```

### 5. Vérifications après déploiement

1. **Health Check**: `https://votre-app.onrender.com/actuator/health`
2. **Page d'accueil**: `https://votre-app.onrender.com/`
3. **Login**: `https://votre-app.onrender.com/login`

### 6. Troubleshooting

**Erreur Maven lors du build Docker** :
```
ERROR: The goal you specified requires a project to execute but there is no POM in this directory (/)
```
**Solutions** :
1. Utilisez le Dockerfile principal corrigé
2. Ou compilez localement et utilisez `Dockerfile.simple`
3. Ou utilisez `Dockerfile.alternative` avec Maven Wrapper

**Si l'application ne démarre pas** :
1. Vérifiez les logs dans Render Dashboard
2. Vérifiez que toutes les variables d'environnement sont configurées
3. Testez la connexion à la base de données

**Si la base de données est inaccessible** :
1. Vérifiez que l'URL de la base de données est correcte
2. Vérifiez que l'application utilise l'URL interne pour Render
3. Vérifiez les credentials de la base de données

**Si le build Docker est trop lent sur Render** :
1. Utilisez `Dockerfile.simple` avec un JAR pré-compilé
2. Optimisez `.dockerignore` pour exclure les fichiers inutiles
3. Considérez utiliser le cache Docker de Render

**Commandes utiles pour debug** :
```bash
# Voir les logs du container
docker logs container-id

# Se connecter au container
docker exec -it container-id sh

# Tester la connexion à la DB
psql "postgresql://gescom_k7fn_user:RQBmTXidsNaUtgD2JQEqYXUcDVZ5aTsf@dpg-d2lha1fdiees73c1k530-a.oregon-postgres.render.com/gescom_k7fn"

# Tester le build local
mvn clean package -DskipTests
docker build -t gescom-test .
docker run -p 8080:8080 gescom-test
```

### 7. Configuration SSL

Render fournit automatiquement les certificats SSL. Votre application sera accessible via HTTPS.

### 8. Custom Domain (optionnel)

Si vous avez un domaine personnalisé :
1. Ajoutez-le dans Render Dashboard
2. Configurez les DNS selon les instructions Render
3. Le certificat SSL sera automatiquement généré

## Notes importantes

- **Cold Start**: Les applications sur plan gratuit peuvent avoir un démarrage lent après inactivité
- **Logs**: Utilisez Render Dashboard pour voir les logs en temps réel  
- **Monitoring**: Le endpoint `/actuator/health` est configuré pour le monitoring
- **Sécurité**: Les sessions sont configurées avec des cookies sécurisés en production

## Fichiers de configuration créés

- `application-render.properties` : Configuration spécifique à Render
- `render.yaml` : Configuration de déploiement automatique (optionnel)
- `Dockerfile` : Image Docker optimisée pour Render
- `.dockerignore` : Exclusions pour optimiser le build