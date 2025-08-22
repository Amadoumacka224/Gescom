// ===================================
// GESCOM - Settings Form Manager
// Version professionnelle 2024
// ===================================

class SettingsFormManager {
    constructor() {
        this.currentStep = 1;
        this.totalSteps = 3;
        this.originalFormData = {};
        this.validationRules = {};
        this.init();
    }

    init() {
        console.log('🚀 Settings Form Manager initialized');
        
        // Initialiser les composants
        this.initializeSelect2();
        this.bindEvents();
        this.setupValidation();
        this.storeOriginalData();
        this.initializeCharCounters();
        
        // Configuration initiale
        this.updateValueInput();
        this.updateSummary();
        
        // Animation d'entrée
        this.animatePageLoad();
    }

    // ===================================
    // INITIALISATION DES COMPOSANTS
    // ===================================
    
    initializeSelect2() {
        if (typeof $ !== 'undefined' && $.fn.select2) {
            $('.form-select').select2({
                theme: 'bootstrap-5',
                width: '100%'
            });
            console.log('✅ Select2 initialisé');
        }
    }

    bindEvents() {
        // Événements de navigation
        this.bindStepNavigation();
        
        // Événements de formulaire
        this.bindFormEvents();
        
        // Événements de validation temps réel
        this.bindValidationEvents();
        
        // Événements spéciaux
        this.bindSpecialEvents();
    }

    bindStepNavigation() {
        // Boutons de navigation
        document.querySelectorAll('.step').forEach(step => {
            step.addEventListener('click', (e) => {
                const stepNumber = parseInt(e.currentTarget.getAttribute('data-step'));
                this.goToStep(stepNumber);
            });
        });
    }

    bindFormEvents() {
        const form = document.getElementById('settingForm');
        if (form) {
            form.addEventListener('submit', this.handleSubmit.bind(this));
        }

        // Changement du type de valeur
        const valueType = document.getElementById('valueType');
        if (valueType) {
            valueType.addEventListener('change', () => {
                this.updateValueInput();
                this.updateSummary();
            });
        }

        // Mise à jour en temps réel des valeurs
        document.addEventListener('input', (e) => {
            if (e.target.matches('input, textarea, select')) {
                this.updatePreview();
                this.updateSummary();
                
                // Validation en temps réel
                if (e.target.hasAttribute('data-validation')) {
                    this.validateField(e.target);
                }
            }
        });
    }

    bindValidationEvents() {
        document.querySelectorAll('[data-validation]').forEach(field => {
            field.addEventListener('blur', () => this.validateField(field));
            field.addEventListener('input', () => this.clearFieldError(field));
        });
    }

    bindSpecialEvents() {
        // Raccourcis clavier
        document.addEventListener('keydown', (e) => {
            // Échap pour fermer les modales
            if (e.key === 'Escape') {
                this.closeAllModals();
            }
            
            // Ctrl+S pour sauvegarder
            if (e.ctrlKey && e.key === 's') {
                e.preventDefault();
                this.handleSubmit();
            }
        });
    }

    // ===================================
    // GESTION DES ÉTAPES
    // ===================================
    
    nextStep() {
        if (this.currentStep < this.totalSteps) {
            if (this.validateCurrentStep()) {
                this.goToStep(this.currentStep + 1);
            }
        }
    }

    previousStep() {
        if (this.currentStep > 1) {
            this.goToStep(this.currentStep - 1);
        }
    }

    goToStep(stepNumber) {
        if (stepNumber < 1 || stepNumber > this.totalSteps) return;
        
        // Validation avant de passer à l'étape suivante
        if (stepNumber > this.currentStep && !this.validateStepsUpTo(stepNumber - 1)) {
            return;
        }
        
        console.log(`📍 Navigation vers l'étape ${stepNumber}`);
        
        // Masquer toutes les sections
        document.querySelectorAll('.form-section').forEach(section => {
            section.classList.remove('active');
        });
        
        // Mettre à jour les indicateurs d'étapes
        document.querySelectorAll('.step').forEach(step => {
            const stepNum = parseInt(step.getAttribute('data-step'));
            step.classList.remove('active', 'completed');
            
            if (stepNum === stepNumber) {
                step.classList.add('active');
            } else if (stepNum < stepNumber) {
                step.classList.add('completed');
            }
        });
        
        // Afficher la section active
        const activeSection = document.getElementById(`step-${stepNumber}`);
        if (activeSection) {
            activeSection.classList.add('active');
        }
        
        this.currentStep = stepNumber;
        this.animateStepTransition();
        
        // Mettre à jour le résumé si on est à la dernière étape
        if (stepNumber === this.totalSteps) {
            this.updateSummary();
        }
    }

    validateCurrentStep() {
        return this.validateStep(this.currentStep);
    }

    validateStep(stepNumber) {
        const stepElement = document.getElementById(`step-${stepNumber}`);
        if (!stepElement) return true;
        
        const fields = stepElement.querySelectorAll('[data-validation]');
        let isValid = true;
        
        fields.forEach(field => {
            if (!this.validateField(field)) {
                isValid = false;
            }
        });
        
        return isValid;
    }

    validateStepsUpTo(stepNumber) {
        for (let i = 1; i <= stepNumber; i++) {
            if (!this.validateStep(i)) {
                return false;
            }
        }
        return true;
    }

    // ===================================
    // GESTION DU TYPE DE VALEUR DYNAMIQUE
    // ===================================
    
    updateValueInput() {
        const valueType = document.getElementById('valueType')?.value;
        const currentValue = document.querySelector('[name="value"]')?.value || '';
        const container = document.getElementById('valueInputContainer');
        const helpText = document.getElementById('valueHelp');
        
        if (!container || !valueType) return;
        
        let inputHtml = '';
        let helpTextContent = '';
        
        switch(valueType) {
            case 'STRING':
                inputHtml = this.createInputHtml('text', currentValue, 'Texte simple');
                helpTextContent = 'Saisir du texte simple';
                break;
                
            case 'TEXT':
                inputHtml = this.createTextareaHtml(currentValue, 'Texte multi-lignes', 3);
                helpTextContent = 'Saisir du texte sur plusieurs lignes';
                break;
                
            case 'INTEGER':
                inputHtml = this.createInputHtml('number', currentValue, '123');
                helpTextContent = 'Saisir un nombre entier (ex: 42, -10)';
                break;
                
            case 'DECIMAL':
                inputHtml = this.createInputHtml('number', currentValue, '3.14', { step: '0.01' });
                helpTextContent = 'Saisir un nombre décimal (ex: 3.14, 19.6)';
                break;
                
            case 'BOOLEAN':
                inputHtml = this.createBooleanHtml(currentValue === 'true');
                helpTextContent = 'Cocher pour activer (true) ou décocher pour désactiver (false)';
                break;
                
            case 'EMAIL':
                inputHtml = this.createInputHtml('email', currentValue, 'exemple@domaine.com');
                helpTextContent = 'Saisir une adresse email valide';
                break;
                
            case 'URL':
                inputHtml = this.createInputHtml('url', currentValue, 'https://exemple.com');
                helpTextContent = 'Saisir une URL complète (avec http:// ou https://)';
                break;
                
            case 'PASSWORD':
                inputHtml = this.createInputHtml('password', currentValue, '••••••••');
                helpTextContent = 'Saisir un mot de passe (sera crypté automatiquement)';
                break;
                
            case 'COLOR':
                inputHtml = this.createColorHtml(currentValue || '#0d6efd');
                helpTextContent = 'Choisir une couleur ou saisir le code hexadécimal';
                break;
                
            case 'DATE':
                inputHtml = this.createInputHtml('date', currentValue, '');
                helpTextContent = 'Sélectionner une date';
                break;
                
            case 'TIME':
                inputHtml = this.createInputHtml('time', currentValue, '');
                helpTextContent = 'Sélectionner une heure';
                break;
                
            case 'JSON':
                inputHtml = this.createTextareaHtml(currentValue, '{"key": "value"}', 4);
                helpTextContent = 'Saisir un objet JSON valide';
                break;
                
            case 'FILE_PATH':
                inputHtml = this.createInputHtml('text', currentValue, '/chemin/vers/fichier');
                helpTextContent = 'Saisir le chemin vers un fichier ou dossier';
                break;
                
            case 'LIST':
                inputHtml = this.createTextareaHtml(currentValue, 'valeur1, valeur2, valeur3', 3);
                helpTextContent = 'Saisir les valeurs séparées par des virgules';
                break;
                
            default:
                inputHtml = this.createInputHtml('text', currentValue, 'Valeur');
                helpTextContent = 'Saisir la valeur';
        }
        
        container.innerHTML = inputHtml;
        if (helpText) {
            helpText.innerHTML = `<i class="fas fa-info-circle"></i> ${helpTextContent}`;
        }
        
        // Re-bind les événements
        this.bindValueEvents();
        this.updatePreview();
    }

    createInputHtml(type, value, placeholder, attrs = {}) {
        const extraAttrs = Object.entries(attrs)
            .map(([key, val]) => `${key}="${val}"`)
            .join(' ');
            
        return `
            <div class="input-wrapper">
                <input type="${type}" class="form-control" name="value" 
                       value="${this.escapeHtml(value)}" 
                       placeholder="${placeholder}" ${extraAttrs}>
                <div class="input-icon">
                    <i class="fas fa-edit"></i>
                </div>
            </div>`;
    }

    createTextareaHtml(value, placeholder, rows) {
        return `
            <div class="input-wrapper">
                <textarea class="form-control" name="value" rows="${rows}" 
                          placeholder="${placeholder}">${this.escapeHtml(value)}</textarea>
                <div class="input-icon">
                    <i class="fas fa-align-left"></i>
                </div>
            </div>`;
    }

    createBooleanHtml(checked) {
        return `
            <div class="boolean-wrapper">
                <div class="toggle-switch large">
                    <input type="checkbox" name="booleanValue" class="toggle-input" 
                           ${checked ? 'checked' : ''} onchange="settingsFormManager.updateBooleanValue(this)">
                    <label class="toggle-label">
                        <span class="toggle-slider"></span>
                    </label>
                </div>
                <div class="boolean-label">
                    <span class="boolean-text">${checked ? 'Activé' : 'Désactivé'}</span>
                    <small class="boolean-desc">Cliquez pour basculer</small>
                </div>
                <input type="hidden" name="value" value="${checked ? 'true' : 'false'}">
            </div>`;
    }

    createColorHtml(value) {
        return `
            <div class="color-wrapper">
                <div class="color-picker">
                    <input type="color" name="colorValue" value="${value}" 
                           onchange="settingsFormManager.updateColorValue(this)">
                    <div class="color-preview" style="background-color: ${value}"></div>
                </div>
                <div class="input-wrapper">
                    <input type="text" class="form-control" name="value" 
                           value="${value}" placeholder="#FF0000"
                           onchange="settingsFormManager.updateColorFromText(this)">
                    <div class="input-icon">
                        <i class="fas fa-palette"></i>
                    </div>
                </div>
            </div>`;
    }

    bindValueEvents() {
        document.querySelectorAll('[name="value"]').forEach(input => {
            input.addEventListener('input', () => {
                this.updatePreview();
                this.updateSummary();
            });
            input.addEventListener('change', () => {
                this.updatePreview();
                this.updateSummary();
            });
        });
    }

    // ===================================
    // GESTIONNAIRES SPÉCIAUX
    // ===================================
    
    updateBooleanValue(checkbox) {
        const hiddenInput = checkbox.parentNode.parentNode.querySelector('input[name="value"]');
        const label = checkbox.parentNode.parentNode.querySelector('.boolean-text');
        
        const isChecked = checkbox.checked;
        hiddenInput.value = isChecked ? 'true' : 'false';
        label.textContent = isChecked ? 'Activé' : 'Désactivé';
        
        this.updatePreview();
        this.updateSummary();
    }

    updateColorValue(colorInput) {
        const textInput = colorInput.parentNode.parentNode.querySelector('input[name="value"]');
        const preview = colorInput.parentNode.querySelector('.color-preview');
        
        textInput.value = colorInput.value;
        preview.style.backgroundColor = colorInput.value;
        
        this.updatePreview();
        this.updateSummary();
    }

    updateColorFromText(textInput) {
        const colorInput = textInput.parentNode.parentNode.querySelector('input[name="colorValue"]');
        const preview = textInput.parentNode.parentNode.querySelector('.color-preview');
        
        if (this.isValidColor(textInput.value)) {
            colorInput.value = textInput.value;
            preview.style.backgroundColor = textInput.value;
        }
        
        this.updatePreview();
        this.updateSummary();
    }

    // ===================================
    // PRÉVISUALISATION
    // ===================================
    
    updatePreview() {
        const valueType = document.getElementById('valueType')?.value;
        const value = document.querySelector('[name="value"]')?.value || '';
        const preview = document.getElementById('valuePreview');
        const content = document.getElementById('previewContent');
        
        if (!preview || !content) return;
        
        if (!value) {
            preview.style.display = 'none';
            return;
        }
        
        let displayValue = this.formatValueForPreview(valueType, value);
        
        content.innerHTML = displayValue;
        preview.style.display = 'block';
    }

    formatValueForPreview(type, value) {
        switch(type) {
            case 'PASSWORD':
                return '<span class="preview-password">••••••••</span>';
                
            case 'BOOLEAN':
                const isTrue = value === 'true';
                return `<span class="preview-boolean ${isTrue ? 'active' : 'inactive'}">
                    <i class="fas fa-${isTrue ? 'check' : 'times'}"></i>
                    ${isTrue ? 'Activé' : 'Désactivé'}
                </span>`;
                
            case 'COLOR':
                return `<div class="preview-color">
                    <div class="color-swatch" style="background-color: ${value}"></div>
                    <span>${value}</span>
                </div>`;
                
            case 'EMAIL':
                return `<a href="mailto:${value}" class="preview-email">
                    <i class="fas fa-envelope"></i> ${value}
                </a>`;
                
            case 'URL':
                return `<a href="${value}" target="_blank" class="preview-url">
                    <i class="fas fa-external-link-alt"></i> ${value}
                </a>`;
                
            case 'LIST':
                const items = value.split(',').map(item => item.trim()).filter(item => item);
                return `<div class="preview-list">
                    ${items.map(item => `<span class="list-item">${this.escapeHtml(item)}</span>`).join('')}
                </div>`;
                
            case 'JSON':
                try {
                    const parsed = JSON.parse(value);
                    return `<pre class="preview-json">${JSON.stringify(parsed, null, 2)}</pre>`;
                } catch (e) {
                    return `<span class="preview-error">JSON invalide: ${e.message}</span>`;
                }
                
            case 'TEXT':
                return `<div class="preview-text">${this.escapeHtml(value).replace(/\n/g, '<br>')}</div>`;
                
            default:
                return `<span class="preview-default">${this.escapeHtml(value)}</span>`;
        }
    }

    // ===================================
    // RÉSUMÉ
    // ===================================
    
    updateSummary() {
        const summaryElements = {
            key: document.getElementById('summary-key'),
            category: document.getElementById('summary-category'),
            type: document.getElementById('summary-type'),
            value: document.getElementById('summary-value'),
            description: document.getElementById('summary-description')
        };
        
        // Récupérer les valeurs
        const values = {
            key: document.getElementById('key')?.value || '-',
            category: document.getElementById('category')?.selectedOptions[0]?.text || '-',
            type: document.getElementById('valueType')?.selectedOptions[0]?.text || '-',
            value: document.querySelector('[name="value"]')?.value || '-',
            description: document.getElementById('description')?.value || '-'
        };
        
        // Mettre à jour les éléments
        Object.entries(summaryElements).forEach(([key, element]) => {
            if (element) {
                let displayValue = values[key];
                
                // Formatage spécial pour certains champs
                if (key === 'value') {
                    const valueType = document.getElementById('valueType')?.value;
                    if (valueType === 'PASSWORD') {
                        displayValue = '••••••••';
                    } else if (displayValue.length > 50) {
                        displayValue = displayValue.substring(0, 50) + '...';
                    }
                }
                
                element.textContent = displayValue;
            }
        });
    }

    // ===================================
    // VALIDATION
    // ===================================
    
    setupValidation() {
        this.validationRules = {
            'required': (value) => value.trim() !== '',
            'key-format': (value) => /^[a-zA-Z][a-zA-Z0-9._-]*$/.test(value),
            'email': (value) => !value || /^[^\s]+@[^\s]+\.[^\s]+$/.test(value),
            'url': (value) => !value || /^https?:\/\/.+/.test(value),
            'integer': (value) => !value || /^-?\d+$/.test(value),
            'decimal': (value) => !value || /^-?\d*\.?\d+$/.test(value),
            'color': (value) => !value || /^#[0-9A-Fa-f]{6}$/.test(value),
            'json': (value) => {
                if (!value) return true;
                try {
                    JSON.parse(value);
                    return true;
                } catch (e) {
                    return false;
                }
            }
        };
    }

    validateField(field) {
        const validation = field.getAttribute('data-validation');
        if (!validation) return true;
        
        const rules = validation.split(',');
        const value = field.value;
        const fieldName = field.getAttribute('placeholder') || field.name || 'Ce champ';
        
        for (const rule of rules) {
            const validator = this.validationRules[rule.trim()];
            if (validator && !validator(value)) {
                this.showFieldError(field, this.getErrorMessage(rule.trim(), fieldName));
                return false;
            }
        }
        
        this.showFieldSuccess(field);
        return true;
    }

    getErrorMessage(rule, fieldName) {
        const messages = {
            'required': `${fieldName} est obligatoire`,
            'key-format': 'Format invalide (lettres, chiffres, points, tirets)',
            'email': 'Format d\'email invalide',
            'url': 'L\'URL doit commencer par http:// ou https://',
            'integer': 'Doit être un nombre entier',
            'decimal': 'Doit être un nombre décimal',
            'color': 'Format couleur invalide (#RRGGBB)',
            'json': 'Format JSON invalide'
        };
        return messages[rule] || 'Valeur invalide';
    }

    showFieldError(field, message) {
        field.classList.remove('is-valid');
        field.classList.add('is-invalid');
        
        const feedback = field.parentNode.parentNode.querySelector('.form-feedback');
        if (feedback) {
            feedback.innerHTML = `<i class="fas fa-exclamation-circle"></i> ${message}`;
            feedback.className = 'form-feedback error';
        }
    }

    showFieldSuccess(field) {
        field.classList.remove('is-invalid');
        field.classList.add('is-valid');
        
        const feedback = field.parentNode.parentNode.querySelector('.form-feedback');
        if (feedback) {
            feedback.innerHTML = `<i class="fas fa-check-circle"></i> Valide`;
            feedback.className = 'form-feedback success';
        }
    }

    clearFieldError(field) {
        field.classList.remove('is-invalid', 'is-valid');
        
        const feedback = field.parentNode.parentNode.querySelector('.form-feedback');
        if (feedback) {
            feedback.innerHTML = '';
            feedback.className = 'form-feedback';
        }
    }

    // ===================================
    // GESTION DU FORMULAIRE
    // ===================================
    
    handleSubmit(event) {
        if (event) event.preventDefault();
        
        console.log('📋 Soumission du formulaire');
        
        // Validation complète
        if (!this.validateForm()) {
            this.showValidationModal();
            return false;
        }
        
        // Animation de soumission
        this.showSubmissionAnimation();
        
        // Soumission réelle
        setTimeout(() => {
            document.getElementById('settingForm').submit();
        }, 1000);
        
        return false;
    }

    validateForm() {
        const fields = document.querySelectorAll('[data-validation]');
        let isValid = true;
        const errors = [];
        
        fields.forEach(field => {
            if (!this.validateField(field)) {
                isValid = false;
                const label = field.parentNode.parentNode.querySelector('.form-label')?.textContent || field.name;
                const feedback = field.parentNode.parentNode.querySelector('.form-feedback')?.textContent;
                if (feedback) {
                    errors.push(`${label}: ${feedback.replace('⚠️', '').trim()}`);
                }
            }
        });
        
        this.validationErrors = errors;
        return isValid;
    }

    showValidationModal() {
        const modal = document.getElementById('validationModal');
        const errorsList = document.getElementById('validationErrors');
        
        if (modal && errorsList && this.validationErrors) {
            errorsList.innerHTML = this.validationErrors
                .map(error => `<li>${error}</li>`)
                .join('');
            
            modal.classList.add('show');
            modal.style.display = 'flex';
        }
    }

    closeValidationModal() {
        const modal = document.getElementById('validationModal');
        if (modal) {
            modal.classList.remove('show');
            modal.style.display = 'none';
        }
    }

    closeAllModals() {
        document.querySelectorAll('.modal.show').forEach(modal => {
            modal.classList.remove('show');
            modal.style.display = 'none';
        });
    }

    // ===================================
    // UTILITAIRES
    // ===================================
    
    storeOriginalData() {
        this.originalFormData = {
            key: document.getElementById('key')?.value || '',
            description: document.getElementById('description')?.value || '',
            category: document.getElementById('category')?.value || '',
            valueType: document.getElementById('valueType')?.value || '',
            value: document.querySelector('[name="value"]')?.value || '',
            sortOrder: document.getElementById('sortOrder')?.value || '',
            isSystem: document.getElementById('isSystem')?.checked || false,
            isEncrypted: document.getElementById('isEncrypted')?.checked || false
        };
    }

    resetForm() {
        if (confirm('Réinitialiser le formulaire aux valeurs d\'origine ?')) {
            // Restaurer les valeurs
            Object.entries(this.originalFormData).forEach(([key, value]) => {
                const element = document.getElementById(key) || document.querySelector(`[name="${key}"]`);
                if (element) {
                    if (element.type === 'checkbox') {
                        element.checked = value;
                    } else {
                        element.value = value;
                    }
                }
            });
            
            // Mettre à jour l'interface
            this.updateValueInput();
            this.updatePreview();
            this.updateSummary();
            
            // Retour à la première étape
            this.goToStep(1);
            
            this.showToast('Formulaire réinitialisé', 'info');
        }
    }

    initializeCharCounters() {
        document.querySelectorAll('[data-counter="true"]').forEach(field => {
            const maxLength = parseInt(field.getAttribute('maxlength')) || 0;
            const counter = field.parentNode.querySelector('.char-counter');
            
            if (counter && maxLength > 0) {
                const updateCounter = () => {
                    const currentLength = field.value.length;
                    counter.textContent = `${currentLength}/${maxLength}`;
                    
                    if (currentLength > maxLength * 0.9) {
                        counter.classList.add('warning');
                    } else {
                        counter.classList.remove('warning');
                    }
                };
                
                field.addEventListener('input', updateCounter);
                updateCounter(); // Initial call
            }
        });
    }

    showSubmissionAnimation() {
        const submitBtn = document.querySelector('.btn-submit');
        if (submitBtn) {
            submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Enregistrement...';
            submitBtn.disabled = true;
        }
    }

    showToast(message, type = 'info', duration = 3000) {
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.innerHTML = `
            <i class="fas fa-${this.getToastIcon(type)}"></i>
            <span>${message}</span>
        `;
        
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
            animation: 'slideIn 0.3s ease-out'
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

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    isValidColor(color) {
        return /^#[0-9A-Fa-f]{6}$/.test(color);
    }

    animatePageLoad() {
        // Animation d'entrée des éléments
        const elements = document.querySelectorAll('.form-section, .form-sidebar, .alerts-container');
        elements.forEach((element, index) => {
            element.style.opacity = '0';
            element.style.transform = 'translateY(20px)';
            setTimeout(() => {
                element.style.transition = 'all 0.5s ease-out';
                element.style.opacity = '1';
                element.style.transform = 'translateY(0)';
            }, index * 100);
        });
    }

    animateStepTransition() {
        const activeSection = document.querySelector('.form-section.active');
        if (activeSection) {
            activeSection.style.animation = 'fadeInUp 0.3s ease-out';
        }
    }
}

// ===================================
// FONCTIONS GLOBALES
// ===================================

let settingsFormManager;

document.addEventListener('DOMContentLoaded', function() {
    settingsFormManager = new SettingsFormManager();
});

// Fonctions globales pour compatibilité
function nextStep() {
    if (settingsFormManager) {
        settingsFormManager.nextStep();
    }
}

function previousStep() {
    if (settingsFormManager) {
        settingsFormManager.previousStep();
    }
}

function resetForm() {
    if (settingsFormManager) {
        settingsFormManager.resetForm();
    }
}

function updateValueInput() {
    if (settingsFormManager) {
        settingsFormManager.updateValueInput();
    }
}

function closeValidationModal() {
    if (settingsFormManager) {
        settingsFormManager.closeValidationModal();
    }
}

// Styles CSS pour animations
const style = document.createElement('style');
style.textContent = `
    @keyframes fadeInUp {
        from {
            opacity: 0;
            transform: translateY(20px);
        }
        to {
            opacity: 1;
            transform: translateY(0);
        }
    }
    
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
    
    .char-counter.warning {
        color: #f59e0b;
        font-weight: 600;
    }
`;
document.head.appendChild(style);