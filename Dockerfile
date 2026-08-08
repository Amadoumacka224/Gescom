
# ==============================================================================
# GESCOM — image de l'API et image du frontal web
#
# Ce Dockerfile porte les deux applications du dépôt, en quatre étapes et deux
# cibles finales que docker-compose construit séparément :
#
#     --target api  : le jar Spring Boot sur un JRE 17
#     --target web  : Caddy servant le SPA compilé
#
# Les étapes de build (maven, node) ne se retrouvent dans aucune des deux images
# finales : ni le JDK, ni Maven, ni node_modules ne partent en production.
# ==============================================================================


# ------------------------------------------------------------------------------
# Étape 1 — compilation de l'API
#
# Le pom est copié seul dans un premier temps : tant qu'il ne change pas, la
# couche de téléchargement des dépendances est réutilisée et un build qui ne
# touche que du code Java repart de src/ sans retélécharger le référentiel.
#
# Les tests sont ignorés ici : ils tournent au poste de développement et en
# intégration continue (`mvn clean install`), pas à la construction de l'image,
# qui doit rester une simple mise en boîte d'un code déjà validé.
# ------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS api-build
WORKDIR /build

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests


# ------------------------------------------------------------------------------
# Étape 2 — image d'exécution de l'API
#
# JRE seul (pas de JDK) et utilisateur non privilégié : une exécution du
# conteneur en root donnerait à une faille applicative les pleins pouvoirs sur
# le système de fichiers de l'image.
#
# spring-boot-devtools est déclaré `optional` dans le pom, le plugin Spring Boot
# l'exclut donc du jar exécutable — rien à neutraliser ici.
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS api

RUN groupadd --system gescom \
    && useradd --system --gid gescom --home-dir /app --shell /usr/sbin/nologin gescom

WORKDIR /app
COPY --from=api-build /build/target/*.jar app.jar
RUN chown gescom:gescom /app/app.jar

USER gescom
EXPOSE 8085

# MaxRAMPercentage plutôt qu'un -Xmx en dur : la JVM se cale sur la mémoire
# réellement allouée au conteneur, quelle que soit la taille de la machine.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]


# ------------------------------------------------------------------------------
# Étape 3 — compilation du SPA
#
# `npm ci` et non `npm install` : l'installation suit strictement le
# package-lock.json, aucune dépendance n'est promue à une version plus récente
# au moment du déploiement.
# ------------------------------------------------------------------------------
FROM node:22-alpine AS front-build
WORKDIR /build

COPY FRONT/package.json FRONT/package-lock.json ./
RUN npm ci

COPY FRONT/ ./
# VITE_API_URL n'est volontairement pas défini : le client tombe sur '/api' en
# relatif (src/services/axios.js) et l'API est servie sous le même domaine que
# le SPA. Aucune origine croisée, donc aucun préflight CORS.
RUN npm run build


# ------------------------------------------------------------------------------
# Étape 4 — frontal web
#
# Caddy sert les fichiers statiques et relaie /api vers le conteneur de l'API.
# Le Caddyfile n'est pas copié dans l'image : docker-compose le monte en lecture
# seule, de façon à pouvoir ajuster la configuration du proxy sans reconstruire.
# ------------------------------------------------------------------------------
FROM caddy:2-alpine AS web
COPY --from=front-build /build/dist /srv
