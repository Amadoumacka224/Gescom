@echo off
REM Script de déploiement GESCOM pour Windows
REM Ce script automatise le déploiement de l'application avec Docker

setlocal enabledelayedexpansion

REM Couleurs pour les messages (utilise des codes ANSI)
set "RED=[91m"
set "GREEN=[92m"
set "YELLOW=[93m"
set "BLUE=[94m"
set "NC=[0m"

REM Variables
set "CLEAN_IMAGES=false"
set "NO_BUILD=false"
set "SHOW_LOGS=false"

REM Afficher l'aide
if "%1"=="--help" (
    echo Usage: %0 [OPTIONS]
    echo.
    echo Options:
    echo   --clean-images    Supprimer les images Docker existantes avant le build
    echo   --no-build        Ne pas reconstruire l'application ^(utiliser l'image existante^)
    echo   --logs            Afficher les logs apres le deploiement
    echo   --help           Afficher cette aide
    echo.
    echo Exemples:
    echo   %0                    # Deploiement standard
    echo   %0 --clean-images     # Deploiement avec nettoyage complet
    echo   %0 --no-build --logs  # Redemarrage rapide avec logs
    exit /b 0
)

REM Parser les arguments
:parse_args
if "%1"=="" goto :start_deployment
if "%1"=="--clean-images" (
    set "CLEAN_IMAGES=true"
    shift
    goto :parse_args
)
if "%1"=="--no-build" (
    set "NO_BUILD=true"
    shift
    goto :parse_args
)
if "%1"=="--logs" (
    set "SHOW_LOGS=true"
    shift
    goto :parse_args
)

echo %RED%[ERROR]%NC% Option inconnue: %1
exit /b 1

:start_deployment
echo.
echo ^🚀 Demarrage du deploiement GESCOM...
echo.

REM Vérification des prérequis
echo %BLUE%[INFO]%NC% Verification des prerequis...

REM Vérifier Docker
docker --version >nul 2>&1
if errorlevel 1 (
    echo %RED%[ERROR]%NC% Docker n'est pas installe. Veuillez installer Docker Desktop.
    pause
    exit /b 1
)

REM Vérifier Docker Compose
docker-compose --version >nul 2>&1
if errorlevel 1 (
    docker compose version >nul 2>&1
    if errorlevel 1 (
        echo %RED%[ERROR]%NC% Docker Compose n'est pas installe.
        pause
        exit /b 1
    )
)

echo %GREEN%[SUCCESS]%NC% Prerequis verifies avec succes

REM Nettoyer l'environnement
echo %BLUE%[INFO]%NC% Nettoyage de l'environnement...

docker-compose down --remove-orphans >nul 2>&1

if "%CLEAN_IMAGES%"=="true" (
    echo %YELLOW%[WARNING]%NC% Suppression des images Docker existantes...
    docker image prune -f >nul 2>&1
    docker rmi gescom-app:latest >nul 2>&1
)

echo %GREEN%[SUCCESS]%NC% Environnement nettoye

REM Construire l'application
if "%NO_BUILD%"=="false" (
    echo %BLUE%[INFO]%NC% Construction de l'application GESCOM...
    docker-compose build --no-cache
    if errorlevel 1 (
        echo %RED%[ERROR]%NC% Echec de la construction de l'application
        pause
        exit /b 1
    )
    echo %GREEN%[SUCCESS]%NC% Application construite avec succes
)

REM Démarrer les services
echo %BLUE%[INFO]%NC% Demarrage des services GESCOM...
docker-compose up -d
if errorlevel 1 (
    echo %RED%[ERROR]%NC% Echec du demarrage des services
    pause
    exit /b 1
)
echo %GREEN%[SUCCESS]%NC% Services demarres avec succes

REM Vérifier la santé des services
echo %BLUE%[INFO]%NC% Verification de la sante des services...

echo %BLUE%[INFO]%NC% Attente du demarrage de PostgreSQL...
set /a timeout=60
:wait_postgres
if %timeout% leq 0 (
    echo %RED%[ERROR]%NC% PostgreSQL n'a pas demarre dans les temps
    pause
    exit /b 1
)
docker-compose exec -T postgres pg_isready -U gescom_user -d gescom >nul 2>&1
if errorlevel 1 (
    timeout /t 2 /nobreak >nul
    set /a timeout-=2
    goto :wait_postgres
)
echo %GREEN%[SUCCESS]%NC% PostgreSQL est pret

echo %BLUE%[INFO]%NC% Attente du demarrage de l'application...
set /a timeout=120
:wait_app
if %timeout% leq 0 (
    echo %RED%[ERROR]%NC% L'application n'a pas demarre dans les temps
    pause
    exit /b 1
)
curl -f http://localhost:8080/actuator/health >nul 2>&1
if errorlevel 1 (
    timeout /t 5 /nobreak >nul
    set /a timeout-=5
    goto :wait_app
)
echo %GREEN%[SUCCESS]%NC% L'application est prête

REM Afficher les informations de connexion
echo.
echo ==================================================
echo %GREEN%GESCOM deploye avec succes!%NC%
echo ==================================================
echo.
echo ^🌐 Application web: http://localhost:8080
echo ^🗄️  Base de donnees PostgreSQL: localhost:5432
echo    - Base: gescom
echo    - Utilisateur: gescom_user
echo    - Mot de passe: gescom_password
echo.
echo ^🔧 PgAdmin ^(interface DB^): http://localhost:5050
echo    - Email: admin@gescom.local
echo    - Mot de passe: admin123
echo.
echo ^📊 Health check: http://localhost:8080/actuator/health
echo.
echo ==================================================
echo.
echo Commandes utiles:
echo   docker-compose logs -f app        # Voir les logs de l'app
echo   docker-compose logs -f postgres   # Voir les logs de la DB
echo   docker-compose down               # Arreter les services
echo   docker-compose up -d              # Redemarrer les services
echo.

REM Afficher les logs si demandé
if "%SHOW_LOGS%"=="true" (
    echo %BLUE%[INFO]%NC% Affichage des logs ^(Ctrl+C pour quitter^)...
    docker-compose logs -f
)

pause