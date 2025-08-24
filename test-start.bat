@echo off
echo ========================================
echo Test de démarrage de l'application GESCOM
echo ========================================

echo Définition de JAVA_HOME...
set JAVA_HOME=C:\Program Files\Java\jdk-17

echo Vérification de Java...
java -version

echo.
echo Tentative de démarrage de l'application sur le port 8085...
echo.

cd /d "C:\MES APP\GESCOM"

mvnw.cmd clean compile spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8085 -Dspring.devtools.livereload.enabled=false"

echo.
echo Application arrêtée.
pause