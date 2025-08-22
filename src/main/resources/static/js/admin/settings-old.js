// ===================================
// GESCOM - Settings Management
// Version professionnelle 2024
// ===================================

class SettingsManager {
    constructor() {
        this.init();
        this.bindEvents();
        this.setupKeyboardShortcuts();
        this.currentView = 'grouped';
        this.searchTimeout = null;
        this.pendingRequests = new Map();
    }

    init() {
        console.log('🚀 Settings Manager initialized');
        
        // Initialiser les composants
        this.initializeSelect2();
        this.initializeTooltips();
        this.validatePageFunctionality();
        
        // Charger les préférences utilisateur
        this.loadUserPreferences();
        
        // Animation d'entrée
        this.animatePageLoad();
    }

    bindEvents() {
        // Événements de recherche et filtres
        this.bindSearchEvents();
        this.bindFilterEvents();
        
        // Événements de vue
        this.bindViewEvents();
        
        // Événements des actions rapides
        this.bindQuickActionEvents();
        
        // Événements de modal
        this.bindModalEvents();
        
        // Événements de tableau
        this.bindTableEvents();
    }

    // ===================================
    // INITIALISATION DES COMPOSANTS
    // ===================================
    
    initializeSelect2() {
        if (typeof $ !== 'undefined' && $.fn.select2) {
            $('.filter-select').select2({
                theme: 'bootstrap-5',
                width: '100%',
                minimumResultsForSearch: -1
            });
            console.log('✅ Select2 initialisé');
        }
    }

    initializeTooltips() {
        // Initialiser les tooltips pour les boutons d'action
        document.querySelectorAll('[title]').forEach(element => {
            element.addEventListener('mouseenter', this.showTooltip.bind(this));
            element.addEventListener('mouseleave', this.hideTooltip.bind(this));
        });
        console.log('✅ Tooltips initialisés');
    }

    validatePageFunctionality() {
        const requiredElements = {
            searchInput: document.getElementById('quickSearch'),
            categoryFilter: document.getElementById('categoryFilter'),
            typeFilter: document.getElementById('typeFilter')
        };

        Object.entries(requiredElements).forEach(([name, element]) => {
            if (element) {
                console.log(`✅ ${name} trouvé et fonctionnel`);
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

    // ===================================
    // GESTION DES ÉVÉNEMENTS
    // ===================================
    
    bindSearchEvents() {
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
    }

    bindFilterEvents() {
        const filters = ['categoryFilter', 'typeFilter'];
        filters.forEach(filterId => {
            const filter = document.getElementById(filterId);
            if (filter) {
                filter.addEventListener('change', () => {
                    this.applyFilters();
                });
            }
        });
    }

    bindViewEvents() {
        document.querySelectorAll('.view-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const view = e.currentTarget.textContent.toLowerCase().includes('groupé') ? 'grouped' : 'list';
                this.switchView(view);
            });
        });
    }

    bindQuickActionEvents() {
        // Événements pour les actions rapides
        const quickActions = {
            'refreshCache': this.refreshCache.bind(this),
            'validateSettings': this.validateSettings.bind(this),
            'initializeDefaults': this.initializeDefaults.bind(this),
            'exportSettings': this.exportSettings.bind(this),
            'showSettingsHelp': this.showSettingsHelp.bind(this)
        };

        Object.entries(quickActions).forEach(([action, handler]) => {
            const elements = document.querySelectorAll(`[onclick*="${action}"], button[title*="${action}"]`);
            elements.forEach(element => {
                element.removeAttribute('onclick');
                element.addEventListener('click', handler);
            });
        });
    }

    bindModalEvents() {
        // Fermeture des modales avec Escape
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                this.closeAllModals();
            }
        });
    }

    bindTableEvents() {
        // Tri des colonnes
        document.querySelectorAll('.sortable').forEach(header => {
            header.addEventListener('click', (e) => {
                this.sortTable(e.currentTarget);
            });
        });

        // Sélection de lignes
        document.querySelectorAll('.setting-row').forEach(row => {
            row.addEventListener('click', (e) => {
                if (!e.target.closest('.action-btn')) {
                    this.toggleRowSelection(row);
                }
            });
        });
    }

    // ===================================
    // RACCOURCIS CLAVIER
    // ===================================
    
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
            
            // Ctrl+1 : Vue groupée
            if (e.ctrlKey && e.key === '1') {
                e.preventDefault();
                this.switchView('grouped');
            }
            
            // Ctrl+2 : Vue liste
            if (e.ctrlKey && e.key === '2') {
                e.preventDefault();
                this.switchView('list');
            }
        });
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

        this.showLoadingState();
        
        // Construire l'URL avec les paramètres
        const url = new URL(window.location);
        if (query) {
            url.searchParams.set('search', query);
        } else {
            url.searchParams.delete('search');
        }
        
        // Conserver les autres filtres
        const categoryFilter = document.getElementById('categoryFilter')?.value;
        const typeFilter = document.getElementById('typeFilter')?.value;
        
        if (categoryFilter) url.searchParams.set('category', categoryFilter);
        if (typeFilter) url.searchParams.set('isSystem', typeFilter);
        
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
        const categoryFilter = document.getElementById('categoryFilter')?.value;
        const typeFilter = document.getElementById('typeFilter')?.value;
        const searchQuery = document.getElementById('quickSearch')?.value;
        
        console.log('🔧 Application des filtres:', { categoryFilter, typeFilter, searchQuery });
        
        const url = new URL(window.location);
        
        // Appliquer les filtres
        if (categoryFilter) {
            url.searchParams.set('category', categoryFilter);
        } else {
            url.searchParams.delete('category');
        }
        
        if (typeFilter) {
            url.searchParams.set('isSystem', typeFilter);
        } else {
            url.searchParams.delete('isSystem');
        }
        
        if (searchQuery) {
            url.searchParams.set('search', searchQuery);
        }
        
        window.location.href = url.toString();
    }

    clearFilters() {
        const url = new URL(window.location);
        url.searchParams.delete('search');
        url.searchParams.delete('category');
        url.searchParams.delete('isSystem');
        
        window.location.href = url.toString();
    }

    // ===================================
    // GESTION DES VUES
    // ===================================
    
    switchView(view) {
        if (this.currentView === view) return;
        
        console.log('👁️ Changement de vue:', view);
        this.currentView = view;
        
        // Mettre à jour les boutons
        document.querySelectorAll('.view-btn').forEach(btn => {
            btn.classList.remove('active');
        });
        
        const activeBtn = view === 'grouped' 
            ? document.querySelector('.view-btn:first-child')
            : document.querySelector('.view-btn:last-child');
        
        if (activeBtn) {
            activeBtn.classList.add('active');
        }
        
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
        this.showConfirmDialog(
            'Rafraîchir le cache',
            'Voulez-vous rafraîchir le cache des paramètres ?',
            () => {
                this.performAction('/admin/settings/refresh-cache', 'Cache rafraîchi avec succès');
            }
        );
    }

    validateSettings() {
        this.showConfirmDialog(
            'Validation des paramètres',
            'Voulez-vous valider tous les paramètres ?',
            () => {
                this.performAction('/admin/settings/validate', 'Paramètres validés avec succès');
            }
        );
    }

    initializeDefaults() {
        this.showConfirmDialog(
            'Initialisation des paramètres',
            'Voulez-vous initialiser les paramètres par défaut ? Cela ne modifiera pas les paramètres existants.',
            () => {
                this.performAction('/admin/settings/initialize', 'Paramètres initialisés avec succès');
            }
        );
    }

    exportSettings() {
        console.log('📤 Export des paramètres');
        this.showLoadingState();
        
        // Créer un lien de téléchargement
        const link = document.createElement('a');
        link.href = '/admin/settings/export';
        link.download = `settings-${new Date().toISOString().split('T')[0]}.json`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        
        this.hideLoadingState();
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
        this.showConfirmDialog(
            'Supprimer le paramètre',
            `Êtes-vous sûr de vouloir supprimer le paramètre "${key}" ?`,
            () => {
                this.performDeleteAction(id);
            }
        );
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
        this.showModal('helpModal');
    }

    showModal(modalId) {
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.classList.add('show');
            modal.style.display = 'flex';
            document.body.style.overflow = 'hidden';
            
            // Focus sur le premier élément focusable
            const focusable = modal.querySelector('button, input, textarea, select, a');
            if (focusable) {
                setTimeout(() => focusable.focus(), 100);
            }
        }
    }

    closeModal(modalId) {
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.classList.remove('show');
            modal.style.display = 'none';
            document.body.style.overflow = '';
        }
    }

    closeAllModals() {
        document.querySelectorAll('.modal.show').forEach(modal => {
            modal.classList.remove('show');
            modal.style.display = 'none';
        });
        document.body.style.overflow = '';
    }

    // ===================================
    // DIALOGUES ET NOTIFICATIONS
    // ===================================
    
    showConfirmDialog(title, message, onConfirm) {
        // Créer un dialogue de confirmation moderne
        const dialog = this.createDialog(title, message, [
            { text: 'Annuler', class: 'btn-outline', action: null },
            { text: 'Confirmer', class: 'btn-primary', action: onConfirm }
        ]);
        
        document.body.appendChild(dialog);
        setTimeout(() => dialog.classList.add('show'), 10);
    }

    createDialog(title, message, buttons) {
        const dialog = document.createElement('div');
        dialog.className = 'modal';
        dialog.innerHTML = `
            <div class="modal-backdrop" onclick="this.parentElement.remove()"></div>
            <div class="modal-content">
                <div class="modal-header">
                    <h3>${title}</h3>
                    <button class="modal-close" onclick="this.closest('.modal').remove()">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="modal-body">
                    <p>${message}</p>
                </div>
                <div class="modal-footer">
                    ${buttons.map(btn => 
                        `<button class="btn ${btn.class}" onclick="${btn.action ? 'this.closest(\'.modal\').dispatchEvent(new CustomEvent(\'confirm\'))' : 'this.closest(\'.modal\').remove()'}">${btn.text}</button>`
                    ).join('')}
                </div>
            </div>
        `;

        if (buttons.some(btn => btn.action)) {
            dialog.addEventListener('confirm', () => {
                const confirmBtn = buttons.find(btn => btn.action);
                if (confirmBtn && confirmBtn.action) {
                    confirmBtn.action();
                }
                dialog.remove();
            });
        }

        return dialog;
    }

    showToast(message, type = 'info', duration = 3000) {
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.innerHTML = `
            <i class="fas fa-${this.getToastIcon(type)}"></i>
            <span>${message}</span>
        `;
        
        // Styles inline pour le toast
        Object.assign(toast.style, {
            position: 'fixed',
            top: '20px',
            right: '20px',
            background: this.getToastColor(type),
            color: 'white',
            padding: '1rem 1.5rem',
            borderRadius: '0.75rem',
            boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1)',
            zIndex: '2000',
            display: 'flex',
            alignItems: 'center',
            gap: '0.75rem',
            animation: 'slideIn 0.3s ease-out',
            minWidth: '300px'
        });
        
        document.body.appendChild(toast);
        
        setTimeout(() => {
            toast.style.animation = 'slideOut 0.3s ease-out forwards';
            setTimeout(() => toast.remove(), 300);
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

    getToastColor(type) {
        const colors = {
            success: '#10b981',
            error: '#ef4444',
            warning: '#f59e0b',
            info: '#06b6d4'
        };
        return colors[type] || colors.info;
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

    showLoadingState() {
        document.body.style.cursor = 'wait';
        const toolbar = document.querySelector('.toolbar');
        if (toolbar) {
            toolbar.style.opacity = '0.7';
        }
    }

    hideLoadingState() {
        document.body.style.cursor = '';
        const toolbar = document.querySelector('.toolbar');
        if (toolbar) {
            toolbar.style.opacity = '1';
        }
    }

    showTooltip(event) {
        const element = event.target;
        const title = element.getAttribute('title');
        if (!title) return;

        const tooltip = document.createElement('div');
        tooltip.className = 'tooltip';
        tooltip.textContent = title;
        
        Object.assign(tooltip.style, {
            position: 'absolute',
            background: '#1f2937',
            color: 'white',
            padding: '0.5rem 0.75rem',
            borderRadius: '0.375rem',
            fontSize: '0.875rem',
            zIndex: '1000',
            pointerEvents: 'none',
            whiteSpace: 'nowrap'
        });

        document.body.appendChild(tooltip);
        
        const rect = element.getBoundingClientRect();
        tooltip.style.left = rect.left + (rect.width / 2) - (tooltip.offsetWidth / 2) + 'px';
        tooltip.style.top = rect.bottom + 8 + 'px';
        
        element.tooltip = tooltip;
        element.removeAttribute('title');
        element.originalTitle = title;
    }

    hideTooltip(event) {
        const element = event.target;
        if (element.tooltip) {
            element.tooltip.remove();
            element.tooltip = null;
        }
        if (element.originalTitle) {
            element.setAttribute('title', element.originalTitle);
        }
    }

    loadUserPreferences() {
        const savedView = localStorage.getItem('settings-view');
        if (savedView && savedView !== this.currentView) {
            this.currentView = savedView;
        }
    }

    animatePageLoad() {
        // Animation des cartes métriques
        const metricCards = document.querySelectorAll('.metric-card');
        metricCards.forEach((card, index) => {
            card.style.opacity = '0';
            card.style.transform = 'translateY(20px)';
            setTimeout(() => {
                card.style.transition = 'all 0.5s ease-out';
                card.style.opacity = '1';
                card.style.transform = 'translateY(0)';
            }, index * 100);
        });

        // Animation des paramètres
        const settingItems = document.querySelectorAll('.setting-card, .setting-row');
        settingItems.forEach((item, index) => {
            item.style.opacity = '0';
            item.style.transform = 'translateX(-20px)';
            setTimeout(() => {
                item.style.transition = 'all 0.3s ease-out';
                item.style.opacity = '1';
                item.style.transform = 'translateX(0)';
            }, 200 + index * 50);
        });
    }

    // ===================================
    // GESTION DU TABLEAU
    // ===================================
    
    sortTable(header) {
        const sortBy = header.getAttribute('data-sort');
        if (!sortBy) return;

        // Mettre à jour les indicateurs de tri
        document.querySelectorAll('.sortable i').forEach(icon => {
            icon.className = 'fas fa-sort';
        });

        const icon = header.querySelector('i');
        const isAscending = header.getAttribute('data-direction') !== 'asc';
        header.setAttribute('data-direction', isAscending ? 'asc' : 'desc');
        icon.className = `fas fa-sort-${isAscending ? 'up' : 'down'}`;

        // Redirection avec tri
        const url = new URL(window.location);
        url.searchParams.set('sort', sortBy);
        url.searchParams.set('direction', isAscending ? 'asc' : 'desc');
        window.location.href = url.toString();
    }

    toggleRowSelection(row) {
        row.classList.toggle('selected');
        
        // Compter les lignes sélectionnées
        const selectedRows = document.querySelectorAll('.setting-row.selected').length;
        
        if (selectedRows > 0) {
            this.showBulkActions();
        } else {
            this.hideBulkActions();
        }
    }

    showBulkActions() {
        let bulkActions = document.querySelector('.bulk-actions');
        if (!bulkActions) {
            bulkActions = this.createBulkActionsBar();
            document.querySelector('.settings-content').prepend(bulkActions);
        }
        bulkActions.style.display = 'flex';
    }

    hideBulkActions() {
        const bulkActions = document.querySelector('.bulk-actions');
        if (bulkActions) {
            bulkActions.style.display = 'none';
        }
    }

    createBulkActionsBar() {
        const bar = document.createElement('div');
        bar.className = 'bulk-actions';
        bar.innerHTML = `
            <div class="bulk-info">
                <span class="selected-count">0</span> paramètres sélectionnés
            </div>
            <div class="bulk-buttons">
                <button class="btn btn-outline" onclick="settingsManager.clearSelection()">
                    Désélectionner tout
                </button>
                <button class="btn btn-primary" onclick="settingsManager.exportSelected()">
                    Exporter sélection
                </button>
            </div>
        `;
        
        Object.assign(bar.style, {
            display: 'none',
            background: 'var(--primary)',
            color: 'white',
            padding: '1rem 1.5rem',
            borderRadius: '0.75rem',
            marginBottom: '1.5rem',
            justifyContent: 'space-between',
            alignItems: 'center'
        });

        return bar;
    }

    // ===================================
    // PAGINATION
    // ===================================
    
    goToPage(page) {
        const url = new URL(window.location);
        url.searchParams.set('page', page);
        window.location.href = url.toString();
    }
}

// ===================================
// FONCTIONS GLOBALES (COMPATIBILITÉ)
// ===================================

let settingsManager;

document.addEventListener('DOMContentLoaded', function() {
    settingsManager = new SettingsManager();
});

// Fonctions globales pour compatibilité avec les templates
function performQuickSearch(query) {
    if (settingsManager) {
        settingsManager.performQuickSearch(query);
    }
}

function applyFilters() {
    if (settingsManager) {
        settingsManager.applyFilters();
    }
}

function switchView(view) {
    if (settingsManager) {
        settingsManager.switchView(view);
    }
}

function editSetting(id) {
    if (settingsManager) {
        settingsManager.editSetting(id);
    }
}

function duplicateSetting(id) {
    if (settingsManager) {
        settingsManager.duplicateSetting(id);
    }
}

function deleteSetting(id, key) {
    if (settingsManager) {
        settingsManager.deleteSetting(id, key);
    }
}

function refreshCache() {
    if (settingsManager) {
        settingsManager.refreshCache();
    }
}

function validateSettings() {
    if (settingsManager) {
        settingsManager.validateSettings();
    }
}

function initializeDefaults() {
    if (settingsManager) {
        settingsManager.initializeDefaults();
    }
}

function exportSettings() {
    if (settingsManager) {
        settingsManager.exportSettings();
    }
}

function showSettingsHelp() {
    if (settingsManager) {
        settingsManager.showSettingsHelp();
    }
}

function closeModal(modalId) {
    if (settingsManager) {
        settingsManager.closeModal(modalId);
    }
}

function clearFilters() {
    if (settingsManager) {
        settingsManager.clearFilters();
    }
}

function goToPage(page) {
    if (settingsManager) {
        settingsManager.goToPage(page);
    }
}

// ===================================
// STYLES CSS POUR ANIMATIONS
// ===================================
const style = document.createElement('style');
style.textContent = `
    @keyframes slideOut {
        from {
            opacity: 1;
            transform: translateX(0);
        }
        to {
            opacity: 0;
            transform: translateX(100px);
        }
    }
    
    .setting-row.selected {
        background-color: rgba(37, 99, 235, 0.1) !important;
        border-left: 3px solid var(--primary);
    }
    
    .bulk-actions {
        animation: slideDown 0.3s ease-out;
    }
    
    @keyframes slideDown {
        from {
            opacity: 0;
            transform: translateY(-20px);
        }
        to {
            opacity: 1;
            transform: translateY(0);
        }
    }
`;
document.head.appendChild(style);