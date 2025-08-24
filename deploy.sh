#!/bin/bash

# Script de déploiement GESCOM
# Ce script automatise le déploiement de l'application avec Docker

set -e

# Couleurs pour les messages
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Fonctions utilitaires
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Vérification des prérequis
check_prerequisites() {
    log_info "Vérification des prérequis..."
    
    # Vérifier Docker
    if ! command -v docker &> /dev/null; then
        log_error "Docker n'est pas installé. Veuillez installer Docker."
        exit 1
    fi
    
    # Vérifier Docker Compose
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        log_error "Docker Compose n'est pas installé. Veuillez installer Docker Compose."
        exit 1
    fi
    
    log_success "Prérequis vérifiés avec succès"
}

# Nettoyer les conteneurs et volumes existants
clean_environment() {
    log_info "Nettoyage de l'environnement..."
    
    # Arrêter et supprimer les conteneurs
    docker-compose down --remove-orphans 2>/dev/null || true
    
    # Supprimer les images obsolètes (optionnel)
    if [ "$1" = "--clean-images" ]; then
        log_warning "Suppression des images Docker existantes..."
        docker image prune -f
        docker rmi gescom-app:latest 2>/dev/null || true
    fi
    
    log_success "Environnement nettoyé"
}

# Construire l'application
build_application() {
    log_info "Construction de l'application GESCOM..."
    
    # Construire l'image Docker
    docker-compose build --no-cache
    
    log_success "Application construite avec succès"
}

# Démarrer les services
start_services() {
    log_info "Démarrage des services GESCOM..."
    
    # Démarrer en mode détaché
    docker-compose up -d
    
    log_success "Services démarrés avec succès"
}

# Vérifier la santé des services
check_health() {
    log_info "Vérification de la santé des services..."
    
    # Attendre que PostgreSQL soit prêt
    log_info "Attente du démarrage de PostgreSQL..."
    timeout=60
    while [ $timeout -gt 0 ]; do
        if docker-compose exec -T postgres pg_isready -U gescom_user -d gescom &>/dev/null; then
            log_success "PostgreSQL est prêt"
            break
        fi
        sleep 2
        timeout=$((timeout-2))
    done
    
    if [ $timeout -le 0 ]; then
        log_error "PostgreSQL n'a pas démarré dans les temps"
        return 1
    fi
    
    # Attendre que l'application soit prête
    log_info "Attente du démarrage de l'application..."
    timeout=120
    while [ $timeout -gt 0 ]; do
        if curl -f http://localhost:8080/actuator/health &>/dev/null; then
            log_success "L'application est prête"
            break
        fi
        sleep 5
        timeout=$((timeout-5))
    done
    
    if [ $timeout -le 0 ]; then
        log_error "L'application n'a pas démarré dans les temps"
        return 1
    fi
}

# Afficher les informations de connexion
show_info() {
    echo ""
    echo "=================================================="
    echo -e "${GREEN}GESCOM déployé avec succès!${NC}"
    echo "=================================================="
    echo ""
    echo "🌐 Application web: http://localhost:8080"
    echo "🗄️  Base de données PostgreSQL: localhost:5432"
    echo "   - Base: gescom"
    echo "   - Utilisateur: gescom_user"
    echo "   - Mot de passe: gescom_password"
    echo ""
    echo "🔧 PgAdmin (interface DB): http://localhost:5050"
    echo "   - Email: admin@gescom.local"
    echo "   - Mot de passe: admin123"
    echo ""
    echo "📊 Health check: http://localhost:8080/actuator/health"
    echo ""
    echo "=================================================="
    echo ""
    echo "Commandes utiles:"
    echo "  docker-compose logs -f app        # Voir les logs de l'app"
    echo "  docker-compose logs -f postgres   # Voir les logs de la DB"
    echo "  docker-compose down               # Arrêter les services"
    echo "  docker-compose up -d              # Redémarrer les services"
    echo ""
}

# Affichage de l'aide
show_help() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --clean-images    Supprimer les images Docker existantes avant le build"
    echo "  --no-build        Ne pas reconstruire l'application (utiliser l'image existante)"
    echo "  --logs            Afficher les logs après le déploiement"
    echo "  --help           Afficher cette aide"
    echo ""
    echo "Exemples:"
    echo "  $0                    # Déploiement standard"
    echo "  $0 --clean-images     # Déploiement avec nettoyage complet"
    echo "  $0 --no-build --logs  # Redémarrage rapide avec logs"
}

# Script principal
main() {
    local clean_images=false
    local no_build=false
    local show_logs=false
    
    # Parser les arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --clean-images)
                clean_images=true
                shift
                ;;
            --no-build)
                no_build=true
                shift
                ;;
            --logs)
                show_logs=true
                shift
                ;;
            --help)
                show_help
                exit 0
                ;;
            *)
                log_error "Option inconnue: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    echo "🚀 Démarrage du déploiement GESCOM..."
    echo ""
    
    # Exécution des étapes
    check_prerequisites
    
    if [ "$clean_images" = true ]; then
        clean_environment --clean-images
    else
        clean_environment
    fi
    
    if [ "$no_build" = false ]; then
        build_application
    fi
    
    start_services
    check_health
    show_info
    
    if [ "$show_logs" = true ]; then
        log_info "Affichage des logs (Ctrl+C pour quitter)..."
        docker-compose logs -f
    fi
}

# Point d'entrée
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi