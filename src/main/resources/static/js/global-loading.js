/**
 * Système de chargement global pour GESCOM
 * Affiche un overlay de chargement lors des interactions avec le serveur
 */

const GlobalLoadingManager = {
    overlay: null,
    isServerOnline: true,
    checkInterval: null,
    isInitialized: false,
    
    /**
     * Initialise le gestionnaire de chargement global
     */
    init() {
        if (this.isInitialized) return;
        
        this.createOverlay();
        this.checkServerStatus();
        this.setupGlobalClickInterceptor();
        this.setupEventListeners();
        
        // Vérifier le statut du serveur périodiquement
        this.checkInterval = setInterval(() => {
            this.checkServerStatus();
        }, 30000); // Toutes les 30 secondes
        
        this.isInitialized = true;
        console.log('GlobalLoadingManager initialisé');
    },
    
    /**
     * Crée l'overlay HTML
     */
    createOverlay() {
        if (document.getElementById('globalLoadingOverlay')) return;
        
        const overlay = document.createElement('div');
        overlay.id = 'globalLoadingOverlay';
        overlay.className = 'global-loading-overlay';
        overlay.innerHTML = `
            <div class="loading-content">
                <div class="loading-spinner"></div>
                <h5 class="mb-2">Chargement en cours...</h5>
                <p class="mb-0">Veuillez patienter pendant que la requête est traitée</p>
            </div>
        `;
        
        document.body.appendChild(overlay);
        this.overlay = overlay;
        
        // Ajouter les styles CSS si pas déjà présents
        if (!document.getElementById('globalLoadingStyles')) {
            this.addStyles();
        }
    },
    
    /**
     * Ajoute les styles CSS nécessaires
     */
    addStyles() {
        const style = document.createElement('style');
        style.id = 'globalLoadingStyles';
        style.textContent = `
            .global-loading-overlay {
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: rgba(0, 0, 0, 0.7);
                z-index: 9999;
                display: none;
                align-items: center;
                justify-content: center;
            }
            
            .loading-content {
                text-align: center;
                color: white;
            }
            
            .loading-spinner {
                width: 60px;
                height: 60px;
                border: 4px solid rgba(255, 255, 255, 0.3);
                border-top: 4px solid #fff;
                border-radius: 50%;
                animation: spin 1s linear infinite;
                margin: 0 auto 20px;
            }
            
            @keyframes spin {
                0% { transform: rotate(0deg); }
                100% { transform: rotate(360deg); }
            }
        `;
        document.head.appendChild(style);
    },
    
    /**
     * Vérifie le statut du serveur
     */
    async checkServerStatus() {
        try {
            // Essayer d'abord l'endpoint de santé
            let response = await fetch('/api/health', { 
                method: 'GET',
                signal: AbortSignal.timeout(3000)
            });
            
            if (!response.ok) {
                // Fallback : essayer la page courante avec HEAD
                response = await fetch(window.location.pathname, { 
                    method: 'HEAD',
                    signal: AbortSignal.timeout(3000)
                });
            }
            
            this.isServerOnline = response.ok;
        } catch (error) {
            // Si toutes les tentatives échouent, considérer le serveur hors ligne
            this.isServerOnline = false;
        }
    },
    
    /**
     * Affiche l'overlay de chargement
     */
    show(message = 'Chargement en cours...') {
        if (!this.overlay) return;
        
        const messageEl = this.overlay.querySelector('h5');
        if (messageEl) messageEl.textContent = message;
        this.overlay.style.display = 'flex';
    },
    
    /**
     * Masque l'overlay de chargement
     */
    hide() {
        if (!this.overlay) return;
        this.overlay.style.display = 'none';
    },
    
    /**
     * Configure l'intercepteur global de clics
     */
    setupGlobalClickInterceptor() {
        document.addEventListener('click', (event) => {
            // Ne pas intercepter les clics sur l'overlay lui-même
            if (event.target.closest('#globalLoadingOverlay')) {
                return;
            }
            
            const target = event.target.closest('a, button, [role="button"]');
            if (!target) return;
            
            // Ignorer certains éléments
            if (this.shouldIgnoreElement(target)) return;
            
            // Si le serveur est en ligne, afficher le chargement
            if (this.isServerOnline) {
                this.handleClickWithLoading(target, event);
            }
        }, true); // Utiliser la phase de capture
    },
    
    /**
     * Détermine si un élément doit être ignoré
     */
    shouldIgnoreElement(element) {
        const ignoredSelectors = [
            '[data-no-loading]',
            '.dropdown-toggle',
            '[data-bs-toggle="dropdown"]',
            '[data-bs-toggle="modal"]',
            '[data-bs-toggle="collapse"]',
            '.btn-close',
            '.alert .btn-close',
            '.nav-link',
            '.navbar-toggler'
        ];
        
        return ignoredSelectors.some(selector => element.matches(selector));
    },
    
    /**
     * Gère le clic avec affichage du chargement
     */
    handleClickWithLoading(target, event) {
        // Déterminer le type d'action et le message approprié
        let loadingMessage = 'Chargement en cours...';
        
        if (target.type === 'submit' || target.closest('form')) {
            loadingMessage = 'Enregistrement en cours...';
        } else if (target.href && !target.href.includes('#')) {
            loadingMessage = 'Redirection en cours...';
        } else if (target.textContent.toLowerCase().includes('supprimer')) {
            loadingMessage = 'Suppression en cours...';
        } else if (target.textContent.toLowerCase().includes('créer')) {
            loadingMessage = 'Création en cours...';
        } else if (target.textContent.toLowerCase().includes('modifier')) {
            loadingMessage = 'Modification en cours...';
        }
        
        this.show(loadingMessage);
        
        // Cacher le chargement après un délai maximum de sécurité
        setTimeout(() => {
            this.hide();
        }, 15000); // 15 secondes maximum
    },
    
    /**
     * Configure les événements pour masquer automatiquement le chargement
     */
    setupEventListeners() {
        // Masquer le chargement lors de changements de page
        window.addEventListener('beforeunload', () => {
            this.hide();
        });
        
        window.addEventListener('pageshow', () => {
            this.hide();
        });
        
        // Masquer le chargement lors d'erreurs
        window.addEventListener('error', () => {
            setTimeout(() => this.hide(), 1000);
        });
        
        // Masquer le chargement quand une nouvelle page se charge
        document.addEventListener('DOMContentLoaded', () => {
            setTimeout(() => this.hide(), 500);
        });
    },
    
    /**
     * Nettoie les ressources utilisées
     */
    destroy() {
        if (this.checkInterval) {
            clearInterval(this.checkInterval);
            this.checkInterval = null;
        }
        
        if (this.overlay) {
            this.overlay.remove();
            this.overlay = null;
        }
        
        const styles = document.getElementById('globalLoadingStyles');
        if (styles) {
            styles.remove();
        }
        
        this.isInitialized = false;
    }
};

// Auto-initialiser quand le DOM est prêt
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        GlobalLoadingManager.init();
    });
} else {
    GlobalLoadingManager.init();
}

// Exposer globalement
window.GlobalLoadingManager = GlobalLoadingManager;