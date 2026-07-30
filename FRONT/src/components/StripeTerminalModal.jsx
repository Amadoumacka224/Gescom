import { useEffect, useState } from 'react';
import { CreditCard, Check, X, Loader2, ShieldCheck, AlertTriangle } from 'lucide-react';
import { toast } from 'react-hot-toast';
import { useTranslation } from 'react-i18next';
import Modal from './Modal';
import paymentService, { TEST_CARDS } from '../services/paymentService';
import { formatCurrency } from '../utils/format';

/**
 * Terminal de paiement carte, adossé à Stripe en mode test.
 *
 * Il déroule le parcours réel d'un paiement carte, dans l'ordre où il se produit :
 *   1. ouverture d'une intention de paiement chez le prestataire ;
 *   2. présentation d'une carte de test, que le prestataire accepte ou refuse ;
 *   3. mise à jour de la facture — faite par le serveur, jamais ici.
 *
 * Les trois étapes restent affichées pendant toute la session : c'est ce qui distingue un
 * refus de carte (étape 2) d'un encaissement qui n'a pas abouti (étape 3), deux situations
 * que le caissier ne traite pas de la même façon.
 *
 * Aucune donnée de carte ne transite : on n'envoie que des jetons de test Stripe, comme une
 * intégration réelle où la saisie a lieu chez le prestataire.
 */

const STEPS = [
  { key: 'intent', labelKey: 'payment.terminal.steps.intent' },
  { key: 'confirm', labelKey: 'payment.terminal.steps.confirm' },
  { key: 'settle', labelKey: 'payment.terminal.steps.settle' },
];

const readMessage = (err) =>
  err.response?.data?.message || err.response?.data?.error || err.message;

const StripeTerminalModal = ({ isOpen, onClose, invoice, amount, onPaid }) => {
  const { t } = useTranslation();
  const [payment, setPayment] = useState(null);
  const [phase, setPhase] = useState('creating'); // creating | ready | confirming | succeeded | failed
  const [error, setError] = useState(null);

  // Une session = une intention. Le compteur sert à en redemander une après un refus : c'est
  // la seule façon de rejouer l'effet, une intention refusée étant close chez le prestataire.
  const [session, setSession] = useState(0);

  // L'intention s'ouvre à l'affichage du terminal : le caissier n'a rien à déclencher de plus
  // que le choix de la carte. `ignore` couvre la fermeture du terminal avant la réponse — et
  // dans ce cas la session doit être abandonnée ici : `handleClose` ne peut pas s'en charger,
  // il n'a encore aucun identifiant à annuler.
  useEffect(() => {
    if (!isOpen) return undefined;
    let ignore = false;

    paymentService
      .createIntent(invoice.id, amount)
      .then(({ data }) => {
        if (ignore) {
          paymentService.cancelIntent(data.id).catch((err) => {
            console.warn('Annulation de la session abandonnée impossible:', err);
          });
          return;
        }
        setPayment(data);
        setPhase('ready');
      })
      .catch((err) => {
        if (ignore) return;
        console.error('Error creating payment intent:', err);
        setError(readMessage(err));
        setPhase('failed');
      });

    return () => {
      ignore = true;
    };
  }, [isOpen, invoice.id, amount, session]);

  /** Nouvelle tentative après un refus : l'intention précédente est close, il en faut une autre. */
  const handleRetry = () => {
    setPayment(null);
    setError(null);
    setPhase('creating');
    setSession((current) => current + 1);
  };

  const handleConfirm = async (paymentMethodId) => {
    setPhase('confirming');
    setError(null);
    try {
      const { data } = await paymentService.confirmIntent(payment.id, paymentMethodId);
      setPayment(data);
      if (data.status === 'SUCCEEDED') {
        setPhase('succeeded');
        toast.success(t('payment.terminal.accepted'));
        onPaid?.(data);
      } else {
        // Refus de l'émetteur : la facture est intacte, une nouvelle tentative est possible.
        setPhase('failed');
        setError(data.failureMessage || t('payment.terminal.declined'));
        // CANCELED, en revanche, signifie que le serveur a clos la session parce que la facture
        // avait bougé entre-temps : la liste affichée n'est plus à jour.
        if (data.status === 'CANCELED') {
          onPaid?.(null);
        }
      }
    } catch (err) {
      console.error('Error confirming payment:', err);
      setError(readMessage(err));
      setPhase('failed');
      // Le serveur a pu clore la session (facture réglée entre-temps) : on rafraîchit la liste
      // pour que le caissier voie l'état réel de la facture.
      onPaid?.(null);
    }
  };

  /**
   * Fermer avec une intention encore ouverte la laisserait bloquer la facture : le serveur
   * n'autorise qu'une session à la fois. On l'abandonne donc explicitement.
   */
  const handleClose = async () => {
    if (payment?.id && payment.status === 'REQUIRES_CONFIRMATION') {
      try {
        await paymentService.cancelIntent(payment.id);
      } catch (err) {
        console.warn('Annulation du paiement impossible:', err);
      }
    }
    onClose();
  };

  const stepState = (key) => {
    if (key === 'intent') {
      if (phase === 'creating') return 'running';
      return payment ? 'done' : 'idle';
    }
    if (key === 'confirm') {
      if (phase === 'confirming') return 'running';
      if (phase === 'succeeded') return 'done';
      // FAILED = carte refusée ; CANCELED = session close par le serveur (facture modifiée).
      // Dans les deux cas c'est à cette étape que le parcours s'est arrêté.
      if (phase === 'failed' && (payment?.status === 'FAILED' || payment?.status === 'CANCELED')) {
        return 'error';
      }
      return 'idle';
    }
    // La facture n'est mise à jour qu'après un paiement accepté.
    if (phase === 'succeeded') return 'done';
    return 'idle';
  };

  const invoiceStatus = payment?.invoice?.status;

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title={t('payment.terminal.title')} size="md">
      <div className="space-y-6">
        {/* Ce que le terminal s'apprête à encaisser */}
        <div className="flex items-start justify-between gap-4 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
          <div className="min-w-0">
            <p className="text-sm text-gray-500 dark:text-gray-400">
              {t('payment.terminal.invoiceLabel', { number: invoice.invoiceNumber })}
            </p>
            <p className="text-2xl font-bold text-gray-900 dark:text-gray-100 tabular-nums">
              {formatCurrency(amount)}
            </p>
          </div>
          {/* Dire franchement si le débit est réel : en mode simulé, rien ne part chez Stripe. */}
          <span className={payment?.simulated === false ? 'badge-info' : 'badge-neutral'}>
            {payment?.simulated === false
              ? t('payment.terminal.modeTest')
              : t('payment.terminal.modeSimulated')}
          </span>
        </div>

        {/* Progression du parcours */}
        <ol className="space-y-3" aria-live="polite">
          {STEPS.map((step) => {
            const state = stepState(step.key);
            return (
              <li key={step.key} className="flex items-center gap-3">
                <span
                  className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${
                    state === 'done'
                      ? 'bg-green-100 text-green-700'
                      : state === 'error'
                        ? 'bg-red-100 text-red-700'
                        : state === 'running'
                          ? 'bg-blue-100 text-blue-700'
                          : 'bg-gray-100 text-gray-400'
                  }`}
                >
                  {state === 'done' && <Check className="w-4 h-4" aria-hidden="true" />}
                  {state === 'error' && <X className="w-4 h-4" aria-hidden="true" />}
                  {state === 'running' && <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />}
                  {state === 'idle' && <span className="w-2 h-2 rounded-full bg-current" />}
                </span>
                <span
                  className={`text-sm ${
                    state === 'idle'
                      ? 'text-gray-400 dark:text-gray-500'
                      : 'text-gray-800 dark:text-gray-200 font-medium'
                  }`}
                >
                  {t(step.labelKey)}
                </span>
              </li>
            );
          })}
        </ol>

        {payment?.intentId && (
          <p className="text-xs text-gray-400 dark:text-gray-500 font-mono break-all">
            {payment.intentId}
          </p>
        )}

        {/* Choix de la carte présentée au terminal */}
        {(phase === 'ready' || phase === 'confirming') && (
          <div className="space-y-2">
            <p className="text-sm font-medium text-gray-700 dark:text-gray-300">
              {t('payment.terminal.pickCard')}
            </p>
            {TEST_CARDS.map((card) => (
              <button
                key={card.id}
                type="button"
                onClick={() => handleConfirm(card.id)}
                disabled={phase === 'confirming'}
                className="w-full flex items-center gap-3 p-3 rounded-lg border border-gray-200 dark:border-gray-700 hover:border-primary-400 hover:bg-primary-50 dark:hover:bg-gray-700 transition-colors text-left disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <CreditCard
                  className={`w-5 h-5 flex-shrink-0 ${
                    card.outcome === 'success' ? 'text-green-600' : 'text-amber-600'
                  }`}
                  aria-hidden="true"
                />
                <span className="min-w-0">
                  <span className="block text-sm font-medium text-gray-800 dark:text-gray-100">
                    {t(card.labelKey)}
                  </span>
                  <span className="block text-xs text-gray-500 dark:text-gray-400 font-mono">
                    {card.hint}
                  </span>
                </span>
              </button>
            ))}
          </div>
        )}

        {/* Issue de la transaction */}
        {phase === 'succeeded' && (
          <div className="rounded-xl bg-green-50 border border-green-200 p-4 space-y-1">
            <p className="flex items-center gap-2 font-semibold text-green-800">
              <ShieldCheck className="w-5 h-5" aria-hidden="true" />
              {t('payment.terminal.accepted')}
            </p>
            {payment.cardBrand && (
              <p className="text-sm text-green-700">
                {payment.cardBrand} •••• {payment.cardLast4}
              </p>
            )}
            <p className="text-sm text-green-700">
              {t('payment.terminal.invoiceLabel', { number: payment.invoice?.invoiceNumber })} :{' '}
              <strong>
                {invoiceStatus ? t(`status.invoice.${invoiceStatus}`, invoiceStatus) : invoiceStatus}
              </strong>
              {invoiceStatus === 'PARTIALLY_PAID' && payment.invoice?.remainingAmount != null && (
                <>
                  {' '}
                  {t('payment.terminal.remaining', {
                    amount: formatCurrency(payment.invoice.remainingAmount),
                  })}
                </>
              )}
            </p>
          </div>
        )}

        {phase === 'failed' && (
          <div className="rounded-xl bg-red-50 border border-red-200 p-4">
            <p className="flex items-center gap-2 font-semibold text-red-800">
              <AlertTriangle className="w-5 h-5" aria-hidden="true" />
              {t('payment.terminal.failed')}
            </p>
            <p className="text-sm text-red-700 mt-1">{error}</p>
            <p className="text-sm text-red-700 mt-1">{t('payment.terminal.invoiceUnchanged')}</p>
          </div>
        )}

        <div className="flex gap-3 pt-2">
          <button type="button" onClick={handleClose} className="btn-secondary flex-1">
            {phase === 'succeeded' ? t('common.close') : t('common.cancel')}
          </button>
          {phase === 'failed' && (
            // Une intention refusée est close côté prestataire : réessayer en ouvre une neuve.
            <button type="button" onClick={handleRetry} className="btn-primary flex-1">
              {t('payment.terminal.retry')}
            </button>
          )}
        </div>
      </div>
    </Modal>
  );
};

export default StripeTerminalModal;
