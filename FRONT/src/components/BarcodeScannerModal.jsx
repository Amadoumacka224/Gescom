import { useEffect, useRef, useState, useCallback } from 'react';
import { BrowserMultiFormatReader } from '@zxing/browser';
import { Camera, ScanLine, Package, AlertCircle, Plus, Minus, RefreshCw } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import Modal from './Modal';
import { formatCurrency } from '../utils/format';

// Traduit une erreur getUserMedia / d'accès caméra en clé de message pour l'utilisateur.
const cameraErrorKey = (err) => {
  const name = err?.name || '';
  if (name === 'NotAllowedError' || name === 'PermissionDeniedError') return 'scanner.errors.denied';
  if (name === 'NotFoundError' || name === 'DevicesNotFoundError' || name === 'OverconstrainedError')
    return 'scanner.errors.noCamera';
  if (name === 'NotReadableError' || name === 'TrackStartError') return 'scanner.errors.busy';
  if (name === 'SecurityError') return 'scanner.errors.insecure';
  return 'scanner.errors.generic';
};

/**
 * Scanner de code-barres par caméra pour l'ajout de produits à une commande.
 *
 * Séparation des responsabilités : ce composant gère UNIQUEMENT la caméra, le décodage
 * temps réel (ZXing) et l'aperçu produit avant ajout. La résolution du produit et l'ajout
 * au panier restent au parent, injectés via `resolveProduct` et `onConfirm`.
 *
 * @param {boolean} isOpen
 * @param {() => void} onClose
 * @param {(code: string) => Promise<{status:'ok'|'unknown'|'inactive'|'error', product?, message?}>} resolveProduct
 * @param {(product, quantity:number) => boolean} onConfirm  retourne true si l'ajout a réussi
 */
const BarcodeScannerModal = ({ isOpen, onClose, resolveProduct, onConfirm }) => {
  const { t } = useTranslation();
  const videoRef = useRef(null);
  const controlsRef = useRef(null);      // IScannerControls ZXing (pour arrêter le flux)
  const scanningRef = useRef(false);     // true tant qu'on accepte une nouvelle lecture
  const lastCodeRef = useRef({ code: null, at: 0 });

  const [phase, setPhase] = useState('starting'); // starting | scanning | looking | preview | error
  const [errorKey, setErrorKey] = useState('');
  const [notice, setNotice] = useState('');        // message transitoire (code inconnu, produit désactivé…)
  const [product, setProduct] = useState(null);
  const [quantity, setQuantity] = useState(1);

  // getUserMedia n'est disponible qu'en contexte sécurisé (HTTPS ou localhost) et avec une caméra.
  // Dérivé au rendu (valeur stable) plutôt que positionné en état depuis l'effet.
  const cameraSupported = typeof window !== 'undefined'
    && window.isSecureContext
    && !!navigator.mediaDevices?.getUserMedia;

  // Réarme le scanner pour lire l'article suivant.
  const resumeScanning = useCallback(() => {
    setProduct(null);
    setPhase('scanning');
    scanningRef.current = true;
  }, []);

  // Traitement d'un code lu. Mémorisé puis exposé via une ref (mise à jour dans un effet) pour
  // que le callback ZXing — dont l'effet caméra ne dépend que de `isOpen` — appelle toujours la
  // dernière version sans jamais relancer le flux vidéo.
  const handleCode = useCallback(async (code) => {
    setNotice('');
    setPhase('looking');
    try {
      const res = await resolveProduct(code);
      if (res?.status === 'ok' && res.product) {
        setProduct(res.product);
        setQuantity(1);
        setPhase('preview');
      } else if (res?.status === 'inactive') {
        setNotice(t('scanner.notices.inactive', { name: res.product?.name || t('common.product') }));
        resumeScanning();
      } else if (res?.status === 'unknown') {
        setNotice(res.message || t('scanner.notices.unknownCode', { code }));
        resumeScanning();
      } else {
        setNotice(res?.message || t('scanner.notices.lookupError'));
        resumeScanning();
      }
    } catch {
      setNotice(t('scanner.notices.lookupError'));
      resumeScanning();
    }
  }, [resolveProduct, resumeScanning, t]);

  const handleCodeRef = useRef(handleCode);
  useEffect(() => { handleCodeRef.current = handleCode; }, [handleCode]);

  // Démarrage/arrêt de la caméra piloté par l'ouverture du modal (déps volontairement limitées
  // à `isOpen` pour ne jamais réinitialiser le flux au gré des re-rendus du parent).
  useEffect(() => {
    if (!isOpen || !cameraSupported) return undefined;

    scanningRef.current = false;
    lastCodeRef.current = { code: null, at: 0 };

    let cancelled = false;
    const reader = new BrowserMultiFormatReader();

    reader.decodeFromConstraints(
      { video: { facingMode: 'environment' } }, // caméra arrière de préférence
      videoRef.current,
      (result) => {
        if (!scanningRef.current || !result) return; // erreurs "NotFound" entre lectures ignorées
        const code = result.getText();
        const now = Date.now();
        // Anti-rebond : on ignore le même code relu dans les 1,2 s.
        if (lastCodeRef.current.code === code && now - lastCodeRef.current.at < 1200) return;
        lastCodeRef.current = { code, at: now };
        scanningRef.current = false;
        handleCodeRef.current(code);
      },
    ).then((controls) => {
      if (cancelled) { controls.stop(); return; }
      controlsRef.current = controls;
      scanningRef.current = true;
      setPhase('scanning');
    }).catch((e) => {
      if (cancelled) return;
      setErrorKey(cameraErrorKey(e));
      setPhase('error');
    });

    return () => {
      cancelled = true;
      scanningRef.current = false;
      try { controlsRef.current?.stop(); } catch { /* flux déjà arrêté */ }
      controlsRef.current = null;
    };
  }, [isOpen, cameraSupported]);

  if (!isOpen) return null;

  // Erreur affichée : caméra non supportée (contexte non sécurisé / pas de caméra) ou échec d'accès.
  const showError = phase === 'error' || !cameraSupported;
  const displayError = !cameraSupported
    ? t('scanner.errors.unsupported')
    : (errorKey && t(errorKey));

  const maxQty = product?.stockQuantity ?? 1;
  const outOfStock = !!product && product.stockQuantity <= 0;

  const handleAdd = () => {
    if (onConfirm?.(product, quantity)) {
      resumeScanning(); // ajout réussi → on enchaîne sur l'article suivant
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('scanner.title')} size="md">
      {showError ? (
        <div className="flex flex-col items-center text-center gap-3 py-6">
          <div className="w-12 h-12 rounded-full bg-red-100 flex items-center justify-center">
            <AlertCircle className="w-6 h-6 text-red-600" />
          </div>
          <p className="text-sm text-gray-700 max-w-sm">{displayError}</p>
          <p className="text-xs text-gray-500 max-w-sm">
            {t('scanner.errorHint')}
          </p>
          <button
            type="button"
            onClick={onClose}
            className="mt-1 px-4 py-2 bg-gray-100 hover:bg-gray-200 rounded-lg text-sm font-semibold text-gray-700"
          >
            {t('common.close')}
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          <div className="relative w-full aspect-video bg-black rounded-xl overflow-hidden">
            {/* Le flux reste monté en continu : le détacher casserait le décodage ZXing. */}
            <video ref={videoRef} className="w-full h-full object-cover" muted playsInline />

            {/* Cadre de visée */}
            <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
              <div className="w-4/5 h-24 border-2 border-white/80 rounded-lg" />
            </div>

            {(phase === 'starting' || phase === 'looking') && (
              <div className="absolute inset-0 flex items-center justify-center gap-2 bg-black/40 text-white text-sm">
                <RefreshCw className="w-4 h-4 animate-spin" />
                {phase === 'starting' ? t('scanner.startingCamera') : t('scanner.lookingUp')}
              </div>
            )}

            {/* Aperçu produit AVANT ajout, en overlay (le flux continue dessous). */}
            {phase === 'preview' && product && (
              <div className="absolute inset-0 bg-white/97 p-4 flex flex-col gap-3 overflow-y-auto">
                <div className="flex items-start gap-3">
                  <div className="w-14 h-14 rounded-lg bg-gray-100 border border-gray-200 flex items-center justify-center overflow-hidden shrink-0">
                    {product.imageUrl
                      ? <img src={product.imageUrl} alt={product.name} className="w-full h-full object-cover" />
                      : <Package className="w-7 h-7 text-gray-300" />}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold text-gray-900 leading-tight">{product.name}</p>
                    <p className="text-xs text-gray-500">{t('products.refShort', { code: product.code })}</p>
                    <div className="mt-1 flex items-center gap-3 text-sm">
                      <span className="font-bold text-blue-600">{formatCurrency(product.sellingPrice)}</span>
                      <span className={`text-xs ${outOfStock ? 'text-red-600 font-semibold' : 'text-gray-500'}`}>
                        {t('scanner.stockLine', { qty: product.stockQuantity, unit: product.unit || '' })}
                      </span>
                    </div>
                  </div>
                </div>

                {outOfStock ? (
                  <p className="flex items-center gap-2 text-sm text-red-600">
                    <AlertCircle className="w-4 h-4 shrink-0" /> {t('scanner.outOfStock')}
                  </p>
                ) : (
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium text-gray-700">{t('common.quantity')}</span>
                    <div className="flex items-center gap-2">
                      <button type="button" onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                        className="w-8 h-8 rounded-lg border border-gray-300 flex items-center justify-center hover:bg-gray-50">
                        <Minus className="w-4 h-4" />
                      </button>
                      <input
                        type="number" min={1} max={maxQty} value={quantity}
                        onChange={(e) => setQuantity(Math.min(maxQty, Math.max(1, parseInt(e.target.value) || 1)))}
                        className="w-16 text-center border border-gray-300 rounded-lg py-1.5 text-sm"
                      />
                      <button type="button" onClick={() => setQuantity((q) => Math.min(maxQty, q + 1))}
                        className="w-8 h-8 rounded-lg border border-gray-300 flex items-center justify-center hover:bg-gray-50">
                        <Plus className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                )}

                <div className="mt-auto flex gap-2">
                  <button type="button" onClick={resumeScanning}
                    className="flex-1 inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-gray-100 hover:bg-gray-200 rounded-lg text-sm font-semibold text-gray-700">
                    <ScanLine className="w-4 h-4" /> {t('scanner.scanAnother')}
                  </button>
                  <button type="button" onClick={handleAdd} disabled={outOfStock}
                    className="flex-1 inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white rounded-lg text-sm font-semibold">
                    <Plus className="w-4 h-4" /> {t('scanner.addToOrder')}
                  </button>
                </div>
              </div>
            )}
          </div>

          <p className="flex items-center justify-center gap-2 text-xs text-gray-500">
            <Camera className="w-3.5 h-3.5" /> {t('scanner.aimHint')}
          </p>

          {notice && (
            <p className="flex items-center gap-2 text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
              <AlertCircle className="w-4 h-4 shrink-0" /> {notice}
            </p>
          )}
        </div>
      )}
    </Modal>
  );
};

export default BarcodeScannerModal;
