/**
 * Script de test pour vérifier les fonctionnalités de GESCOM
 * Test de l'upload d'images et des paramètres
 */

document.addEventListener('DOMContentLoaded', function() {
    console.log('🚀 Test des fonctionnalités GESCOM');
    
    // Test 1: Vérification de l'upload d'images
    testImageUpload();
    
    // Test 2: Vérification des paramètres
    testSettingsSystem();
    
    // Test 3: Vérification de l'affichage des images
    testImageDisplay();
});

function testImageUpload() {
    console.log('📸 Test de l\'upload d\'images...');
    
    // Vérifier que les éléments d'upload existent
    const uploadZone = document.getElementById('uploadZone');
    const imageFile = document.getElementById('imageFile');
    const browseBtn = document.getElementById('browseBtn');
    
    if (uploadZone && imageFile && browseBtn) {
        console.log('✅ Éléments d\'upload trouvés');
        
        // Vérifier que les événements sont attachés
        if (uploadZone.onclick) {
            console.log('✅ Zone de drop fonctionnelle');
        }
        
        // Test de validation des types de fichiers
        const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp', 'image/gif'];
        console.log('✅ Types de fichiers autorisés:', allowedTypes);
        
    } else {
        console.warn('⚠️ Éléments d\'upload manquants');
    }
}

function testSettingsSystem() {
    console.log('⚙️ Test du système de paramètres...');
    
    // Vérifier que les URL des paramètres sont correctes
    const settingsLinks = document.querySelectorAll('a[href*="/admin/settings"]');
    if (settingsLinks.length > 0) {
        console.log(`✅ ${settingsLinks.length} liens vers les paramètres trouvés`);
    }
    
    // Test des fonctions de paramètres si disponibles
    if (typeof editSetting === 'function') {
        console.log('✅ Fonction editSetting disponible');
    }
    
    if (typeof deleteSetting === 'function') {
        console.log('✅ Fonction deleteSetting disponible');
    }
    
    if (typeof refreshCache === 'function') {
        console.log('✅ Fonction refreshCache disponible');
    }
}

function testImageDisplay() {
    console.log('🖼️ Test d\'affichage des images...');
    
    // Vérifier les images de produits dans la liste
    const productImages = document.querySelectorAll('img[alt*="product"], img[src*="uploads/images"]');
    console.log(`📋 ${productImages.length} images de produits trouvées`);
    
    // Test de fallback pour les images manquantes
    productImages.forEach((img, index) => {
        img.onerror = function() {
            console.log(`⚠️ Image ${index + 1} non trouvée:`, this.src);
            // Afficher l'icône de fallback
            this.style.display = 'none';
            const fallback = this.nextElementSibling;
            if (fallback) {
                fallback.style.display = 'flex';
            }
        };
        
        img.onload = function() {
            console.log(`✅ Image ${index + 1} chargée:`, this.src);
        };
    });
}

// Test des URLs de serveur d'images
function testImageServer() {
    console.log('🔗 Test du serveur d\'images...');
    
    // Test d'une image d'exemple
    const testImage = new Image();
    testImage.onload = function() {
        console.log('✅ Serveur d\'images fonctionnel');
    };
    testImage.onerror = function() {
        console.warn('⚠️ Serveur d\'images non accessible');
    };
    testImage.src = '/uploads/images/sample-product.svg';
}

// Fonction utilitaire pour tester l'upload
function simulateFileUpload(file) {
    console.log('🧪 Simulation d\'upload de fichier:', file.name);
    
    // Vérifications de base
    const maxSize = 5 * 1024 * 1024; // 5MB
    const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp', 'image/gif'];
    
    if (file.size > maxSize) {
        console.error('❌ Fichier trop volumineux');
        return false;
    }
    
    if (!allowedTypes.includes(file.type)) {
        console.error('❌ Type de fichier non autorisé');
        return false;
    }
    
    console.log('✅ Fichier valide pour upload');
    return true;
}

// Export des fonctions pour utilisation externe
window.GESCOM_TEST = {
    testImageUpload,
    testSettingsSystem,
    testImageDisplay,
    testImageServer,
    simulateFileUpload
};

console.log('🎯 Tests GESCOM initialisés');