// ===================================
// GESCOM - Settings Management (Corrigé)
// Version simplifiée et fonctionnelle
// ===================================

class SettingsManagerFixed {
    constructor() {
        this.init();
        this.bindEvents();
        this.setupKeyboardShortcuts();
        this.currentView = this.getCurrentView();
        this.searchTimeout = null;
    }

    init() {
        console.log('🚀 Settings Manager Fixed initialized');
        
        // Validation des éléments de la page
        this.validatePageElements();
        
        // Charger les préférences utilisateur
        this.loadUserPreferences();
        
        console.log('✅ Settings Manager Fixed prêt');
    }

    getCurrentView() {
        // Déterminer la vue actuelle depuis l'URL ou le bouton actif
        const urlParams = new URLSearchParams(window.location.search);
        return urlParams.get('view') || 'grouped';
    }

    validatePageElements() {
        const requiredElements = {
            searchInput: document.getElementById('quickSearch'),
            categoryFilter: document.getElementById('categoryFilter'),
            typeFilter: document.getElementById('typeFilter')
        };

        Object.entries(requiredElements).forEach(([name, element]) => {
            if (element) {
                console.log(`✅ ${name} trouvé`);
            } else {
                console.warn(`⚠️ ${name} non trouvé`);
            }
        });

        // Vérifier CSRF token
        const csrfMeta = document.querySelector('meta[name="_csrf"]');
        if (csrfMeta) {
            console.log('✅ CSRF token configuré');
        } else {
            console.warn('⚠️ CSRF token manquant');
        }
    }

    bindEvents() {
        // Événements de recherche
        const searchInput = document.getElementById('quickSearch');
        if (searchInput) {
            searchInput.addEventListener('input', (e) => {
                this.debounceSearch(e.target.value);
            });
            
            searchInput.addEventListener('keydown', (e) => {
                if (e.key === 'Escape') {
                    this.clearSearch();
                }
            });
        }

        // Événements de filtres
        const categoryFilter = document.getElementById('categoryFilter');
        if (categoryFilter) {
            categoryFilter.addEventListener('change', () => {
                this.applyFilters();
            });
        }

        const typeFilter = document.getElementById('typeFilter');
        if (typeFilter) {
            typeFilter.addEventListener('change', () => {
                this.applyFilters();
            });
        }

        // Événements des boutons de vue
        document.querySelectorAll('[onclick*="switchView"]').forEach(btn => {
            btn.removeAttribute('onclick');
            btn.addEventListener('click', (e) => {
                const view = e.currentTarget.textContent.toLowerCase().includes('groupé') ? 'grouped' : 'list';
                this.switchView(view);
            });
        });

        console.log('✅ Événements liés');
    }

    setupKeyboardShortcuts() {
        document.addEventListener('keydown', (e) => {
            // Ctrl+N : Nouveau paramètre
            if (e.ctrlKey && e.key === 'n') {
                e.preventDefault();
                window.location.href = '/admin/settings/new';
            }
            
            // Ctrl+F : Focus sur la recherche
            if (e.ctrlKey && e.key === 'f') {
                e.preventDefault();
                const searchInput = document.getElementById('quickSearch');
                if (searchInput) {
                    searchInput.focus();
                    searchInput.select();
                }
            }
            
            // Escape : Fermer les modales
            if (e.key === 'Escape') {
                const modals = document.querySelectorAll('.modal.show');
                modals.forEach(modal => {
                    const bsModal = bootstrap.Modal.getInstance(modal);
                    if (bsModal) {
                        bsModal.hide();
                    }
                });
            }
        });
        
        console.log('✅ Raccourcis clavier configurés');
    }

    // ===================================
    // GESTION DE LA RECHERCHE
    // ===================================
    
    debounceSearch(query) {
        clearTimeout(this.searchTimeout);
        this.searchTimeout = setTimeout(() => {
            this.performQuickSearch(query);
        }, 300);
    }

    performQuickSearch(query) {
        console.log('🔍 Recherche:', query);
        
        if (query.length < 2 && query.length > 0) {
            return;
        }

        // Construire l'URL avec les paramètres
        const url = new URL(window.location);
        if (query && query.trim() !== '') {
            url.searchParams.set('search', query.trim());
        } else {
            url.searchParams.delete('search');
        }
        
        // Conserver les autres filtres
        const categoryFilter = document.getElementById('categoryFilter');
        const typeFilter = document.getElementById('typeFilter');
        
        if (categoryFilter && categoryFilter.value) {
            url.searchParams.set('category', categoryFilter.value);
        }
        if (typeFilter && typeFilter.value) {
            url.searchParams.set('isSystem', typeFilter.value);
        }
        
        // Redirection avec les nouveaux paramètres
        window.location.href = url.toString();
    }

    clearSearch() {
        const searchInput = document.getElementById('quickSearch');
        if (searchInput) {
            searchInput.value = '';
            this.performQuickSearch('');
        }
    }

    // ===================================
    // GESTION DES FILTRES
    // ===================================
    
    applyFilters() {
        const categoryFilter = document.getElementById('categoryFilter');
        const typeFilter = document.getElementById('typeFilter');
        const searchInput = document.getElementById('quickSearch');
        
        const categoryValue = categoryFilter ? categoryFilter.value : '';
        const typeValue = typeFilter ? typeFilter.value : '';
        const searchQuery = searchInput ? searchInput.value : '';
        
        console.log('🔧 Application des filtres:', { categoryValue, typeValue, searchQuery });
        
        const url = new URL(window.location);
        
        // Appliquer les filtres
        if (categoryValue) {
            url.searchParams.set('category', categoryValue);
        } else {
            url.searchParams.delete('category');
        }
        
        if (typeValue) {
            url.searchParams.set('isSystem', typeValue);
        } else {
            url.searchParams.delete('isSystem');
        }
        
        if (searchQuery && searchQuery.trim() !== '') {
            url.searchParams.set('search', searchQuery.trim());
        } else {
            url.searchParams.delete('search');
        }
        
        window.location.href = url.toString();
    }

    clearFilters() {
        const url = new URL(window.location);
        url.searchParams.delete('search');
        url.searchParams.delete('category');
        url.searchParams.delete('isSystem');
        
        // Effacer les champs aussi
        const searchInput = document.getElementById('quickSearch');
        const categoryFilter = document.getElementById('categoryFilter');
        const typeFilter = document.getElementById('typeFilter');
        
        if (searchInput) searchInput.value = '';
        if (categoryFilter) categoryFilter.value = '';
        if (typeFilter) typeFilter.value = '';
        
        window.location.href = url.toString();
    }

    // ===================================
    // GESTION DES VUES
    // ===================================
    
    switchView(view) {
        if (this.currentView === view) return;
        
        console.log('👁️ Changement de vue:', view);
        this.currentView = view;
        
        // Sauvegarder la préférence
        localStorage.setItem('settings-view', view);
        
        // Redirection avec le paramètre de vue
        const url = new URL(window.location);
        url.searchParams.set('view', view);
        window.location.href = url.toString();
    }

    // ===================================
    // ACTIONS RAPIDES
    // ===================================
    
    refreshCache() {
        if (!confirm('Voulez-vous rafraîchir le cache des paramètres ?')) {
            return;
        }
        
        this.performAction('/admin/settings/refresh-cache', 'Cache rafraîchi avec succès');
    }

    validateSettings() {
        if (!confirm('Voulez-vous valider tous les paramètres ?')) {
            return;
        }
        
        this.performAction('/admin/settings/validate', 'Paramètres validés avec succès');
    }

    initializeDefaults() {
        if (!confirm('Voulez-vous initialiser les paramètres par défaut ? Cela ne modifiera pas les paramètres existants.')) {
            return;
        }
        
        this.performAction('/admin/settings/initialize', 'Paramètres initialisés avec succès');
    }

    exportSettings() {
        console.log('📤 Export des paramètres');
        
        // Créer un lien de téléchargement
        const link = document.createElement('a');
        link.href = '/admin/settings/export';
        link.download = `settings-${new Date().toISOString().split('T')[0]}.json`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        
        this.showToast('Export terminé', 'success');
    }

    // ===================================
    // ACTIONS SUR LES PARAMÈTRES
    // ===================================
    
    editSetting(id) {
        window.location.href = `/admin/settings/edit/${id}`;
    }

    duplicateSetting(id) {
        window.location.href = `/admin/settings/duplicate/${id}`;
    }

    deleteSetting(id, key) {
        if (!confirm(`Êtes-vous sûr de vouloir supprimer le paramètre "${key}" ?`)) {
            return;
        }
        
        this.performDeleteAction(id);
    }

    performDeleteAction(id) {
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = `/admin/settings/delete/${id}`;

        const csrfToken = document.querySelector('meta[name="_csrf"]');
        if (csrfToken) {
            const csrfInput = document.createElement('input');
            csrfInput.type = 'hidden';
            csrfInput.name = '_csrf';
            csrfInput.value = csrfToken.getAttribute('content');
            form.appendChild(csrfInput);
        }

        document.body.appendChild(form);
        form.submit();
    }

    // ===================================
    // GESTION DES MODALES
    // ===================================
    
    showSettingsHelp() {
        const helpModal = document.getElementById('helpModal');
        if (helpModal) {
            const modal = new bootstrap.Modal(helpModal);
            modal.show();
        }
    }

    // ===================================
    // PAGINATION
    // ===================================
    
    goToPage(page) {
        const url = new URL(window.location);
        url.searchParams.set('page', page);
        window.location.href = url.toString();
    }

    // ===================================
    // UTILITAIRES
    // ===================================
    
    performAction(url, successMessage) {
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = url;

        const csrfToken = document.querySelector('meta[name="_csrf"]');
        if (csrfToken) {
            const csrfInput = document.createElement('input');
            csrfInput.type = 'hidden';
            csrfInput.name = '_csrf';
            csrfInput.value = csrfToken.getAttribute('content');
            form.appendChild(csrfInput);
        }

        document.body.appendChild(form);
        form.submit();
    }

    showToast(message, type = 'info', duration = 3000) {
        // Utiliser les alertes Bootstrap natives pour simplicité
        const alertClass = {
            success: 'alert-success',
            error: 'alert-danger',
            warning: 'alert-warning',
            info: 'alert-info'
        };

        const alertDiv = document.createElement('div');
        alertDiv.className = `alert ${alertClass[type] || alertClass.info} alert-dismissible fade show position-fixed`;
        alertDiv.style.cssText = 'top: 20px; right: 20px; z-index: 9999; min-width: 300px;';
        alertDiv.innerHTML = `
            <i class="fas fa-${this.getToastIcon(type)} me-2"></i>
            ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        `;
        
        document.body.appendChild(alertDiv);
        
        setTimeout(() => {
            if (alertDiv.parentNode) {
                const alert = new bootstrap.Alert(alertDiv);
                alert.close();
            }
        }, duration);
    }

    getToastIcon(type) {
        const icons = {
            success: 'check-circle',
            error: 'exclamation-circle',
            warning: 'exclamation-triangle',
            info: 'info-circle'
        };
        return icons[type] || icons.info;
    }

    loadUserPreferences() {
        const savedView = localStorage.getItem('settings-view');
        if (savedView && savedView !== this.currentView) {
            this.currentView = savedView;
        }
    }
}

// ===================================
// INITIALISATION
// ===================================

let settingsManagerFixed;

document.addEventListener('DOMContentLoaded', function() {
    settingsManagerFixed = new SettingsManagerFixed();
    
    // Exposer les fonctions globalement pour compatibilité
    window.performQuickSearch = function(query) {
        settingsManagerFixed.performQuickSearch(query);
    };
    
    window.applyFilters = function() {
        settingsManagerFixed.applyFilters();
    };
    
    window.switchView = function(view) {
        settingsManagerFixed.switchView(view);
    };
    
    window.editSetting = function(id) {
        settingsManagerFixed.editSetting(id);
    };
    
    window.duplicateSetting = function(id) {
        settingsManagerFixed.duplicateSetting(id);
    };
    
    window.deleteSetting = function(id, key) {
        settingsManagerFixed.deleteSetting(id, key);
    };
    
    window.refreshCache = function() {
        settingsManagerFixed.refreshCache();
    };
    
    window.validateSettings = function() {
        settingsManagerFixed.validateSettings();
    };
    
    window.initializeDefaults = function() {
        settingsManagerFixed.initializeDefaults();
    };
    
    window.exportSettings = function() {
        settingsManagerFixed.exportSettings();
    };
    
    window.showSettingsHelp = function() {
        settingsManagerFixed.showSettingsHelp();
    };
    
    window.clearFilters = function() {
        settingsManagerFixed.clearFilters();
    };
    
    window.goToPage = function(page) {
        settingsManagerFixed.goToPage(page);
    };
    
    console.log('🎉 Settings Manager Fixed - Toutes les fonctions sont prêtes !');
});