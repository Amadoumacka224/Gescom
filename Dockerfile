
# ==============================================================================
# GESCOM - image de l'API et image du frontal web
#
# Ce Dockerfile porte les deux applications du dépôt, en quatre étapes et deux
# cibles finales que docker-compose construit séparément :
#
#     --target web  : Caddy servant le SPA compilé
#     --target api  : le jar Spring Boot sur un JRE 17
#
# Les étapes de build (node, maven) ne se retrouvent dans aucune des deux images
# finales : ni le JDK, ni Maven, ni node_modules ne partent en production.
#
# L'ordre des étapes n'est pas indifférent : l'API est écrite en dernier parce
# qu'une construction sans cible explicite s'arrête sur la dernière étape du
# fichier. Render ne sait pas désigner d'étape intermédiaire - il n'y a pas
# d'équivalent de `--target` dans render.yaml - donc l'API doit être ce que
# `docker build .` produit par défaut. docker-compose, lui, nomme ses deux
# cibles et n'est pas concerné. Ne pas remonter les étapes de l'API sans
# reprendre le déploiement Render.
#
# Chaque image de base est épinglée par son empreinte (@sha256:…) : le tag seul
# est mobile, et deux constructions du même commit à un mois d'écart
# partiraient sinon de bases différentes. Le tag reste écrit pour la lecture ;
# Dependabot propose les nouvelles empreintes (voir .github/dependabot.yml).
# ==============================================================================


# ------------------------------------------------------------------------------
# Étape 1 - compilation du SPA
#
# `npm ci` et non `npm install` : l'installation suit strictement le
# package-lock.json, aucune dépendance n'est promue à une version plus récente
# au moment du déploiement.
#
# Node 24 et non 22 : @zxing/library, tiré par le scanner de codes-barres
# (@zxing/browser), déclare `engines.node >= 24`. Sur Node 22 npm n'émet qu'un
# avertissement EBADENGINE et le build aboutit, mais rien ne garantit que la
# bibliothèque se comporte comme prévu sur un moteur qu'elle exclut.
# ------------------------------------------------------------------------------
FROM node:24-alpine@sha256:06d7adf3e0ef61a13f1d28682726366f97a15aa1888c934d3ecec86c33918b53 AS front-build
WORKDIR /build

COPY FRONT/package.json FRONT/package-lock.json ./
RUN npm ci

COPY FRONT/ ./
# VITE_API_URL n'est volontairement pas défini : le client tombe sur '/api' en
# relatif (src/services/axios.js) et l'API est servie sous le même domaine que
# le SPA. Aucune origine croisée, donc aucun préflight CORS.
RUN npm run build


# ------------------------------------------------------------------------------
# Étape 2 - frontal web
#
# Caddy sert les fichiers statiques et relaie /api vers le conteneur de l'API.
# Le Caddyfile n'est pas copié dans l'image : docker-compose le monte en lecture
# seule, de façon à pouvoir ajuster la configuration du proxy sans reconstruire.
# ------------------------------------------------------------------------------
FROM caddy:2-alpine@sha256:eb4d35b7452d1ab6171fbdbe8d9275b2301dad6dad92115d91e19fb9b24d510f AS web
COPY --from=front-build /build/dist /srv


# ------------------------------------------------------------------------------
# Étape 3 - compilation de l'API
#
# Le pom est copié seul dans un premier temps : tant qu'il ne change pas, la
# couche de téléchargement des dépendances est réutilisée et un build qui ne
# touche que du code Java repart de src/ sans retélécharger le référentiel.
#
# Les tests sont ignorés ici : ils tournent au poste de développement et en
# intégration continue (`mvn clean install`), pas à la construction de l'image,
# qui doit rester une simple mise en boîte d'un code déjà validé.
# ------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17@sha256:1a352420f7aba21f5ad08df31bab55f74c013fb491f1ae8ab1dd7ff9ed698584 AS api-build
WORKDIR /build

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests


# ------------------------------------------------------------------------------
# Étape 4 - image d'exécution de l'API (étape par défaut, voir l'en-tête)
#
# JRE seul (pas de JDK) et utilisateur non privilégié : une exécution du
# conteneur en root donnerait à une faille applicative les pleins pouvoirs sur
# le système de fichiers de l'image.
#
# spring-boot-devtools est déclaré `optional` dans le pom, le plugin Spring Boot
# l'exclut donc du jar exécutable - rien à neutraliser ici.
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy@sha256:b3054a20e1a851574cdb1692dd0db5310c647e62214a2a7abecbec1b9e3ae8f1 AS api

RUN groupadd --system gescom \
    && useradd --system --gid gescom --home-dir /app --shell /usr/sbin/nologin gescom

WORKDIR /app
COPY --from=api-build /build/target/*.jar app.jar
RUN chown gescom:gescom /app/app.jar

USER gescom

# Port d'écoute du déploiement docker-compose, celui que le Caddyfile relaie.
# Sur Render, $PORT prend le dessus (voir application-render.properties) et cette
# déclaration n'est pas utilisée.
EXPOSE 8085

# MaxRAMPercentage plutôt qu'un -Xmx en dur : la JVM se cale sur la mémoire
# réellement allouée au conteneur, quelle que soit la taille de la machine.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
