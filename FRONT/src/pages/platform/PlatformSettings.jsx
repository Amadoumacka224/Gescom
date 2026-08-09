import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { SlidersHorizontal, KeyRound } from 'lucide-react';
import Button from '../../components/Button';
import FormInput from '../../components/FormInput';
import { extractErrorMessage } from '../../utils/apiError';
import platformService from '../../services/platformService';

/**
 * Paramètres du back-office propriétaire.
 *
 * Deux blocs seulement, chacun répondant à un manque concret :
 *
 *  - les seuils du tableau de bord, qui étaient des constantes de `PlatformMetricsService`
 *    et exigeaient donc un redéploiement pour répondre à une question purement commerciale
 *    (« à partir de quand un renouvellement est-il "à venir" ? ») ;
 *  - le compte propriétaire, seul endroit d'où son mot de passe peut être changé :
 *    `PlatformAdminBootstrap` ne réécrit jamais un compte existant, si bien que modifier
 *    `.env` après la première création n'a aucun effet.
 *
 * Rien sur l'identité de l'éditeur ni sur la politique d'accès : le premier n'a aucun usage
 * tant qu'aucune facture d'abonnement n'est émise, la seconde suppose un ordonnanceur qui
 * n'existe pas dans le projet.
 */

const Section = ({ icon: Icon, title, description, children }) => (
  <section className="card p-5">
    <header className="mb-5 flex items-start gap-3">
      <div className="mt-0.5 rounded-lg bg-primary-50 p-2 text-primary-600 dark:bg-primary-500/10 dark:text-primary-400">
        <Icon className="h-5 w-5" />
      </div>
      <div>
        <h2 className="text-sm font-semibold text-gray-900 dark:text-gray-100">{title}</h2>
        <p className="mt-0.5 text-sm text-gray-500 dark:text-gray-400">{description}</p>
      </div>
    </header>
    {children}
  </section>
);

const EMPTY_ACCOUNT = { email: '', currentPassword: '', newPassword: '', confirmPassword: '' };

const PlatformSettings = () => {
  const { t } = useTranslation();

  const [thresholds, setThresholds] = useState(null);
  const [account, setAccount] = useState(EMPTY_ACCOUNT);
  const [identity, setIdentity] = useState(null);
  const [loading, setLoading] = useState(true);
  const [savingThresholds, setSavingThresholds] = useState(false);
  const [savingAccount, setSavingAccount] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await platformService.getSettings();
      setThresholds({
        renewalWindowDays: String(data.renewalWindowDays),
        trialAlertDays: String(data.trialAlertDays),
        revenueHistoryMonths: String(data.revenueHistoryMonths),
        overduePenaltyPoints: String(data.overduePenaltyPoints),
        failedPaymentPenaltyPoints: String(data.failedPaymentPenaltyPoints),
      });
      setIdentity(data.account);
      setAccount({ ...EMPTY_ACCOUNT, email: data.account?.email ?? '' });
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    load();
  }, [load]);

  const setThreshold = (field) => (event) =>
    setThresholds((current) => ({ ...current, [field]: event.target.value }));

  const setAccountField = (field) => (event) =>
    setAccount((current) => ({ ...current, [field]: event.target.value }));

  const submitThresholds = async (event) => {
    event.preventDefault();
    setSavingThresholds(true);
    try {
      await platformService.updateSettings({
        renewalWindowDays: Number(thresholds.renewalWindowDays),
        trialAlertDays: Number(thresholds.trialAlertDays),
        revenueHistoryMonths: Number(thresholds.revenueHistoryMonths),
        overduePenaltyPoints: Number(thresholds.overduePenaltyPoints),
        failedPaymentPenaltyPoints: Number(thresholds.failedPaymentPenaltyPoints),
      });
      toast.success(t('platform.settings.thresholdsSaved'));
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setSavingThresholds(false);
    }
  };

  const submitAccount = async (event) => {
    event.preventDefault();
    // Vérifié ici parce que le serveur ne reçoit jamais la confirmation : c'est une
    // précaution de saisie, pas une règle métier.
    if (account.newPassword && account.newPassword !== account.confirmPassword) {
      toast.error(t('platform.settings.passwordMismatch'));
      return;
    }
    setSavingAccount(true);
    try {
      const { data } = await platformService.updateAccount({
        email: account.email,
        currentPassword: account.currentPassword,
        newPassword: account.newPassword || null,
      });
      setIdentity(data.account);
      // Les champs de mot de passe sont vidés systématiquement : les laisser remplis
      // inviterait à un second envoi involontaire.
      setAccount({ ...EMPTY_ACCOUNT, email: data.account?.email ?? '' });
      toast.success(t('platform.settings.accountSaved'));
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setSavingAccount(false);
    }
  };

  if (loading || !thresholds) {
    return (
      <div className="space-y-6">
        <div className="h-8 w-64 animate-pulse rounded bg-gray-200 dark:bg-gray-700" />
        <div className="h-64 animate-pulse rounded-xl bg-gray-100 dark:bg-gray-800" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {t('platform.settings.title')}
        </h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {t('platform.settings.subtitle')}
        </p>
      </div>

      <Section
        icon={SlidersHorizontal}
        title={t('platform.settings.thresholds')}
        description={t('platform.settings.thresholdsHint')}
      >
        <form onSubmit={submitThresholds} className="space-y-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <FormInput
              label={t('platform.settings.renewalWindow')}
              name="renewalWindowDays"
              type="number"
              min="1"
              max="365"
              value={thresholds.renewalWindowDays}
              onChange={setThreshold('renewalWindowDays')}
              hint={t('platform.settings.renewalWindowHint')}
              required
            />
            <FormInput
              label={t('platform.settings.trialAlert')}
              name="trialAlertDays"
              type="number"
              min="1"
              max="90"
              value={thresholds.trialAlertDays}
              onChange={setThreshold('trialAlertDays')}
              hint={t('platform.settings.trialAlertHint')}
              required
            />
            <FormInput
              label={t('platform.settings.revenueHistory')}
              name="revenueHistoryMonths"
              type="number"
              min="1"
              max="60"
              value={thresholds.revenueHistoryMonths}
              onChange={setThreshold('revenueHistoryMonths')}
              hint={t('platform.settings.revenueHistoryHint')}
              required
            />
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FormInput
              label={t('platform.settings.overduePenalty')}
              name="overduePenaltyPoints"
              type="number"
              min="0"
              max="50"
              value={thresholds.overduePenaltyPoints}
              onChange={setThreshold('overduePenaltyPoints')}
              required
            />
            <FormInput
              label={t('platform.settings.failedPenalty')}
              name="failedPaymentPenaltyPoints"
              type="number"
              min="0"
              max="50"
              value={thresholds.failedPaymentPenaltyPoints}
              onChange={setThreshold('failedPaymentPenaltyPoints')}
              required
            />
          </div>

          <p className="rounded-lg border border-gray-200 bg-gray-50 p-3 text-sm text-gray-600 dark:border-gray-700 dark:bg-gray-800/60 dark:text-gray-400">
            {t('platform.settings.healthFormula')}
          </p>

          <div className="flex justify-end border-t border-gray-200 pt-4 dark:border-gray-700">
            <Button type="submit" loading={savingThresholds}>
              {t('common.saveChanges')}
            </Button>
          </div>
        </form>
      </Section>

      <Section
        icon={KeyRound}
        title={t('platform.settings.account')}
        description={t('platform.settings.accountHint')}
      >
        <form onSubmit={submitAccount} className="space-y-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FormInput
              label={t('platform.settings.username')}
              name="ownerUsername"
              value={identity?.username ?? ''}
              onChange={() => {}}
              disabled
              hint={t('platform.settings.usernameLocked')}
            />
            <FormInput
              label={t('common.email')}
              name="ownerEmail"
              type="email"
              value={account.email}
              onChange={setAccountField('email')}
              required
            />
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <FormInput
              label={t('platform.settings.currentPassword')}
              name="currentPassword"
              type="password"
              value={account.currentPassword}
              onChange={setAccountField('currentPassword')}
              required
              hint={t('platform.settings.currentPasswordHint')}
            />
            <FormInput
              label={t('platform.settings.newPassword')}
              name="newPassword"
              type="password"
              value={account.newPassword}
              onChange={setAccountField('newPassword')}
              hint={t('platform.settings.newPasswordHint')}
            />
            <FormInput
              label={t('platform.settings.confirmPassword')}
              name="confirmPassword"
              type="password"
              value={account.confirmPassword}
              onChange={setAccountField('confirmPassword')}
            />
          </div>

          <div className="flex justify-end border-t border-gray-200 pt-4 dark:border-gray-700">
            <Button type="submit" loading={savingAccount}>
              {t('common.saveChanges')}
            </Button>
          </div>
        </form>
      </Section>
    </div>
  );
};

export default PlatformSettings;
