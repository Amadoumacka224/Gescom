// src/main/resources/static/js/app.js

/**
 * Application JavaScript principale
 * Fonctionnalités communes à toute l'application
 */

(function() {
    'use strict';

    // Configuration globale
    const App = {
        config: {
            alertAutoHideDelay: 5000,
            animationDelay: 100,
            confirmDeleteMessage: 'Êtes-vous sûr de vouloir supprimer cet élément ? Cette action est irréversible.',
            loadingText: 'Chargement...',
            errorText: 'Une erreur s\'est produite'
        },

        // État de l'application
        state: {
            isLoading: false,
            currentUser: null
        },

        // Initialisation
        init: function() {
            console.log('🚀 Initialisation de l\'application...');

            this.initEventListeners();
            this.initAlerts();
            this.initForms();
            this.initTables();
            this.initTooltips();
            this.initModals();
            this.initAnimations();

            console.log('✅ Application initialisée avec succès');
        },

        // Event listeners globaux
        initEventListeners: function() {
            // Auto-hide des alertes
            this.initAlerts();

            // Confirmation de suppression
            this.initDeleteConfirmation();

            // Navigation active
            this.initActiveNavigation();

            // Search en temps réel
            this.initLiveSearch();

            // Auto-save forms
            this.initAutoSave();
        },

        // Gestion des alertes
        initAlerts: function() {
            const alerts = document.querySelectorAll('.alert:not(.alert-permanent)');

            alerts.forEach(alert => {
                // Auto-hide après délai
                setTimeout(() => {
                    this.hideAlert(alert);
                }, this.config.alertAutoHideDelay);
            });
        },

        hideAlert: function(alert) {
            if (!alert) return;

            alert.style.transition = 'all 0.5s ease';
            alert.style.opacity = '0';
            alert.style.transform = 'translateY(-20px)';

            setTimeout(() => {
                if (alert.parentNode) {
                    alert.parentNode.removeChild(alert);
                }
            }, 500);
        },

        // Afficher une alerte dynamique
        showAlert: function(message, type = 'info', container = null) {
            const alertTypes = {
                'success': { icon: 'check-circle', class: 'alert-success' },
                'error': { icon: 'exclamation-triangle', class: 'alert-danger' },
                'warning': { icon: 'exclamation-triangle', class: 'alert-warning' },
                'info': { icon: 'info-circle', class: 'alert-info' }
            };

            const alertConfig = alertTypes[type] || alertTypes.info;

            const alertHtml = `
                <div class="alert ${alertConfig.class} alert-dismissible fade show animate-fade-in" role="alert">
                    <i class="fas fa-${alertConfig.icon} me-2"></i>
                    ${message}
                    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
                </div>
            `;

            const targetContainer = container || document.querySelector('.container-fluid');
            if (targetContainer) {
                const alertElement = document.createElement('div');
                alertElement.innerHTML = alertHtml;
                targetContainer.insertBefore(alertElement.firstElementChild, targetContainer.firstChild);
            }
        },

        // Confirmation de suppression
        initDeleteConfirmation: function() {
            document.addEventListener('click', (e) => {
                if (e.target.matches('.btn-delete, [data-action="delete"]')) {
                    e.preventDefault();

                    const customMessage = e.target.getAttribute('data-confirm-message');
                    const message = customMessage || this.config.confirmDeleteMessage;

                    if (confirm(message)) {
                        // Si c'est un lien, suivre le lien
                        if (e.target.tagName === 'A') {
                            window.location.href = e.target.href;
                        }
                        // Si c'est un bouton dans un form, soumettre le form
                        else if (e.target.tagName === 'BUTTON') {
                            const form = e.target.closest('form');
                            if (form) {
                                form.submit();
                            }
                        }
                    }
                }
            });
        },

        // Navigation active
        initActiveNavigation: function() {
            const navLinks = document.querySelectorAll('.nav-link');
            const currentPath = window.location.pathname;

            navLinks.forEach(link => {
                const linkPath = new URL(link.href).pathname;

                if (linkPath === currentPath ||
                    (linkPath !== '/' && currentPath.startsWith(linkPath))) {
                    link.classList.add('active');
                }
            });
        },

        // Recherche en temps réel
        initLiveSearch: function() {
            const searchInputs = document.querySelectorAll('[data-live-search]');

            searchInputs.forEach(input => {
                let timeout;

                input.addEventListener('input', (e) => {
                    clearTimeout(timeout);

                    timeout = setTimeout(() => {
                        this.performLiveSearch(e.target);
                    }, 300);
                });
            });
        },

        performLiveSearch: function(input) {
            const searchTerm = input.value.toLowerCase().trim();
            const targetSelector = input.getAttribute('data-search-target');
            const targets = document.querySelectorAll(targetSelector);

            targets.forEach(target => {
                const searchableText = target.textContent.toLowerCase();
                const shouldShow = !searchTerm || searchableText.includes(searchTerm);

                target.style.display = shouldShow ? '' : 'none';

                // Animation fade
                if (shouldShow) {
                    target.style.opacity = '0';
                    target.style.transform = 'translateY(10px)';
                    setTimeout(() => {
                        target.style.transition = 'all 0.3s ease';
                        target.style.opacity = '1';
                        target.style.transform = 'translateY(0)';
                    }, 50);
                }
            });
        },

        // Formulaires
        initForms: function() {
            // Validation en temps réel
            const forms = document.querySelectorAll('form[data-validate]');

            forms.forEach(form => {
                form.addEventListener('input', (e) => {
                    this.validateField(e.target);
                });

                form.addEventListener('submit', (e) => {
                    if (!this.validateForm(form)) {
                        e.preventDefault();
                    }
                });
            });

            // Boutons de soumission avec loading
            this.initSubmitButtons();
        },

        validateField: function(field) {
            const isValid = field.checkValidity();

            field.classList.toggle('is-valid', isValid && field.value.length > 0);
            field.classList.toggle('is-invalid', !isValid && field.value.length > 0);

            return isValid;
        },

        validateForm: function(form) {
            let isValid = true;
            const fields = form.querySelectorAll('input, select, textarea');

            fields.forEach(field => {
                if (!this.validateField(field)) {
                    isValid = false;
                }
            });

            return isValid;
        },

        initSubmitButtons: function() {
            document.addEventListener('submit', (e) => {
                const form = e.target;
                const submitBtn = form.querySelector('[type="submit"]');

                if (submitBtn && !form.hasAttribute('data-no-loading')) {
                    this.showButtonLoading(submitBtn);
                }
            });
        },

        showButtonLoading: function(button) {
            const originalText = button.innerHTML;
            const loadingHtml = `
                <span class="spinner me-2"></span>
                ${this.config.loadingText}
            `;

            button.innerHTML = loadingHtml;
            button.disabled = true;

            // Restaurer le bouton après 10 secondes maximum
            setTimeout(() => {
                button.innerHTML = originalText;
                button.disabled = false;
            }, 10000);
        },

        // Tables
        initTables: function() {
            // Tri des colonnes
            const sortableHeaders = document.querySelectorAll('[data-sortable]');

            sortableHeaders.forEach(header => {
                header.style.cursor = 'pointer';
                header.innerHTML += ' <i class="fas fa-sort text-muted"></i>';

                header.addEventListener('click', () => {
                    this.sortTable(header);
                });
            });
        },

        sortTable: function(header) {
            const table = header.closest('table');
            const tbody = table.querySelector('tbody');
            const rows = Array.from(tbody.querySelectorAll('tr'));
            const columnIndex = Array.from(header.parentNode.children).indexOf(header);
            const isAsc = !header.classList.contains('sort-asc');

            // Réinitialiser tous les headers
            header.parentNode.querySelectorAll('th').forEach(th => {
                th.classList.remove('sort-asc', 'sort-desc');
                const icon = th.querySelector('i');
                if (icon) icon.className = 'fas fa-sort text-muted';
            });

            // Marquer le header actuel
            header.classList.add(isAsc ? 'sort-asc' : 'sort-desc');
            const icon = header.querySelector('i');
            if (icon) {
                icon.className = `fas fa-sort-${isAsc ? 'up' : 'down'} text-primary`;
            }

            // Trier les lignes
            rows.sort((a, b) => {
                const aText = a.children[columnIndex].textContent.trim();
                const bText = b.children[columnIndex].textContent.trim();

                const aValue = isNaN(aText) ? aText.toLowerCase() : parseFloat(aText);
                const bValue = isNaN(bText) ? bText.toLowerCase() : parseFloat(bText);

                if (isAsc) {
                    return aValue > bValue ? 1 : -1;
                } else {
                    return aValue < bValue ? 1 : -1;
                }
            });

            // Réinsérer les lignes triées
            rows.forEach(row => tbody.appendChild(row));
        },

        // Tooltips
        initTooltips: function() {
            const tooltipElements = document.querySelectorAll('[data-bs-toggle="tooltip"]');
            tooltipElements.forEach(element => {
                new bootstrap.Tooltip(element);
            });
        },

        // Modals
        initModals: function() {
            // Auto-focus sur les modals
            document.addEventListener('shown.bs.modal', (e) => {
                const modal = e.target;
                const autofocusElement = modal.querySelector('[autofocus]');
                if (autofocusElement) {
                    autofocusElement.focus();
                }
            });
        },

        // Animations
        initAnimations: function() {
            // Intersection Observer pour les animations au scroll
            const observerOptions = {
                threshold: 0.1,
                rootMargin: '0px 0px -50px 0px'
            };

            const observer = new IntersectionObserver((entries) => {
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        entry.target.classList.add('animate-fade-in');
                        observer.unobserve(entry.target);
                    }
                });
            }, observerOptions);

            // Observer les éléments avec data-animate
            document.querySelectorAll('[data-animate]').forEach(el => {
                observer.observe(el);
            });
        },

        // Auto-save
        initAutoSave: function() {
            const autoSaveForms = document.querySelectorAll('[data-autosave]');

            autoSaveForms.forEach(form => {
                let timeout;

                form.addEventListener('input', () => {
                    clearTimeout(timeout);

                    timeout = setTimeout(() => {
                        this.autoSaveForm(form);
                    }, 2000);
                });
            });
        },

        autoSaveForm: function(form) {
            const formData = new FormData(form);
            const saveUrl = form.getAttribute('data-autosave-url') || form.action;

            // Sauvegarder en localStorage comme fallback
            const formId = form.id || 'autosave-form';
            const data = Object.fromEntries(formData.entries());
            localStorage.setItem(`autosave-${formId}`, JSON.stringify(data));

            // Afficher un indicateur de sauvegarde
            this.showSaveIndicator(form);
        },

        showSaveIndicator: function(form) {
            const indicator = document.createElement('div');
            indicator.className = 'alert alert-success alert-sm fade show position-fixed';
            indicator.style.cssText = 'top: 20px; right: 20px; z-index: 1060; padding: 0.5rem 1rem;';
            indicator.innerHTML = '<i class="fas fa-check me-2"></i>Sauvegardé automatiquement';

            document.body.appendChild(indicator);

            setTimeout(() => {
                indicator.remove();
            }, 2000);
        },

        // Utilitaires
        utils: {
            // Formatter une date
            formatDate: function(date, locale = 'fr-FR') {
                return new Date(date).toLocaleDateString(locale, {
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric'
                });
            },

            // Formatter un nombre
            formatNumber: function(number, locale = 'fr-FR') {
                return new Intl.NumberFormat(locale).format(number);
            },

            // Formatter une devise
            formatCurrency: function(amount, currency = 'EUR', locale = 'fr-FR') {
                return new Intl.NumberFormat(locale, {
                    style: 'currency',
                    currency: currency
                }).format(amount);
            },

            // Debounce function
            debounce: function(func, wait) {
                let timeout;
                return function executedFunction(...args) {
                    const later = () => {
                        clearTimeout(timeout);
                        func(...args);
                    };
                    clearTimeout(timeout);
                    timeout = setTimeout(later, wait);
                };
            },

            // Copier du texte dans le presse-papiers
            copyToClipboard: function(text) {
                if (navigator.clipboard) {
                    navigator.clipboard.writeText(text).then(() => {
                        App.showAlert('Copié dans le presse-papiers', 'success');
                    });
                } else {
                    // Fallback pour les anciens navigateurs
                    const textArea = document.createElement('textarea');
                    textArea.value = text;
                    document.body.appendChild(textArea);
                    textArea.select();
                    document.execCommand('copy');
                    document.body.removeChild(textArea);
                    App.showAlert('Copié dans le presse-papiers', 'success');
                }
            },

            // Générer un ID unique
            generateId: function(prefix = 'id') {
                return `${prefix}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
            }
        },

        // API helper
        api: {
            // Faire un appel AJAX
            request: function(url, options = {}) {
                const defaults = {
                    method: 'GET',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-Requested-With': 'XMLHttpRequest'
                    }
                };

                const config = { ...defaults, ...options };

                return fetch(url, config)
                    .then(response => {
                        if (!response.ok) {
                            throw new Error(`HTTP error! status: ${response.status}`);
                        }
                        return response.json();
                    })
                    .catch(error => {
                        console.error('API Error:', error);
                        App.showAlert(App.config.errorText, 'error');
                        throw error;
                    });
            }
        }
    };

    // Initialisation quand le DOM est prêt
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => App.init());
    } else {
        App.init();
    }

    // Exposer App globalement pour utilisation dans d'autres scripts
    window.App = App;

})();