import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { Plus, Pencil, EyeOff, Eye, Trash2 } from 'lucide-react';
import Table from '../../components/Table';
import Modal from '../../components/Modal';
import ConfirmModal from '../../components/ConfirmModal';
import Button from '../../components/Button';
import FormInput from '../../components/FormInput';
import { badgeClass } from '../../constants/statusBadges';
import { formatCurrency } from '../../utils/format';
import { extractErrorMessage } from '../../utils/apiError';
import platformService from '../../services/platformService';

/**
 * Catalogue commercial.
 *
 * Point à ne pas perdre de vue en modifiant un tarif : **les contrats en cours ne bougent
 * pas**. Le montant est figé à la souscription, une révision du catalogue ne vaut donc que
 * pour les souscriptions à venir — sans quoi changer un prix réécrirait le MRR passé.
 * L'écran le rappelle explicitement dans le formulaire.
 *
 * Retirer une formule se fait en la désactivant. La suppression n'est proposée que sur une
 * formule que personne n'a jamais souscrite, l'historique des contrats y faisant référence.
 */

const EMPTY_FORM = {
  code: '',
  name: '',
  description: '',
  monthlyPrice: '',
  yearlyPrice: '',
  maxUsers: '',
  maxProducts: '',
  trialDays: '14',
  sortOrder: '0',
};

const PlatformPlans = () => {
  const { t } = useTranslation();

  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [confirm, setConfirm] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await platformService.getPlans();
      setPlans(response.data ?? []);
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    setForm(EMPTY_FORM);
    setModalOpen(true);
  };

  const openEdit = (plan) => {
    setEditing(plan);
    setForm({
      code: plan.code ?? '',
      name: plan.name ?? '',
      description: plan.description ?? '',
      monthlyPrice: String(plan.monthlyPrice ?? ''),
      yearlyPrice: String(plan.yearlyPrice ?? ''),
      maxUsers: plan.maxUsers != null ? String(plan.maxUsers) : '',
      maxProducts: plan.maxProducts != null ? String(plan.maxProducts) : '',
      trialDays: String(plan.trialDays ?? '14'),
      sortOrder: String(plan.sortOrder ?? '0'),
    });
    setModalOpen(true);
  };

  const setField = (field) => (event) =>
    setForm((current) => ({ ...current, [field]: event.target.value }));

  const submit = async (event) => {
    event.preventDefault();
    setSaving(true);
    try {
      const payload = {
        code: form.code.trim().toUpperCase(),
        name: form.name,
        description: form.description || null,
        monthlyPrice: Number(form.monthlyPrice),
        yearlyPrice: Number(form.yearlyPrice),
        // Vide vaut « illimité » : le serveur attend null, pas zéro.
        maxUsers: form.maxUsers === '' ? null : Number(form.maxUsers),
        maxProducts: form.maxProducts === '' ? null : Number(form.maxProducts),
        trialDays: Number(form.trialDays),
        active: editing ? editing.active : true,
        sortOrder: Number(form.sortOrder),
      };
      if (editing) {
        await platformService.updatePlan(editing.id, payload);
        toast.success(t('platform.plans.updated'));
      } else {
        await platformService.createPlan(payload);
        toast.success(t('platform.plans.created'));
      }
      setModalOpen(false);
      load();
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setSaving(false);
    }
  };

  const runAction = async () => {
    if (!confirm) return;
    try {
      await confirm.run();
      toast.success(confirm.successMessage);
      load();
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setConfirm(null);
    }
  };

  const limitLabel = (value) => (value == null ? t('platform.plans.unlimited') : value);

  const columns = [
    {
      key: 'name',
      label: t('platform.plans.plan'),
      render: (plan) => (
        <div className="min-w-0">
          <p className="truncate font-medium text-gray-900 dark:text-gray-100">{plan.name}</p>
          <code className="text-xs text-gray-500 dark:text-gray-400">{plan.code}</code>
        </div>
      ),
    },
    {
      key: 'description',
      label: t('common.description'),
      className: 'hidden xl:table-cell',
      render: (plan) => (
        <span className="text-sm text-gray-500 dark:text-gray-400">{plan.description}</span>
      ),
    },
    {
      key: 'pricing',
      label: t('platform.plans.pricing'),
      render: (plan) => (
        <div>
          <p className="text-sm font-semibold text-gray-900 dark:text-gray-100">
            {t('platform.plans.perMonth', { value: formatCurrency(plan.monthlyPrice) })}
          </p>
          <p className="text-xs text-gray-500 dark:text-gray-400">
            {t('platform.plans.perYear', { value: formatCurrency(plan.yearlyPrice) })}
          </p>
        </div>
      ),
    },
    {
      key: 'limits',
      label: t('platform.plans.limits'),
      className: 'hidden lg:table-cell',
      render: (plan) => (
        <span className="text-sm text-gray-500 dark:text-gray-400">
          {t('platform.plans.limitsValue', {
            users: limitLabel(plan.maxUsers),
            products: limitLabel(plan.maxProducts),
          })}
        </span>
      ),
    },
    {
      key: 'subscriptionCount',
      label: t('platform.plans.subscribers'),
      className: 'hidden md:table-cell',
      render: (plan) => (
        <span className="text-sm text-gray-900 dark:text-gray-100">{plan.subscriptionCount}</span>
      ),
    },
    {
      key: 'active',
      label: t('platform.plans.status'),
      render: (plan) => (
        <span className={badgeClass(plan.active ? 'success' : 'neutral')}>
          {t(plan.active ? 'platform.plans.onSale' : 'platform.plans.withdrawn')}
        </span>
      ),
    },
  ];

  const actions = (plan) => (
    <div className="flex items-center justify-end gap-1">
      <button
        onClick={() => openEdit(plan)}
        className="rounded p-1.5 text-gray-500 hover:bg-gray-100 hover:text-gray-900 dark:hover:bg-gray-700 dark:hover:text-gray-100"
        title={t('common.edit')}
      >
        <Pencil className="h-4 w-4" />
      </button>
      <button
        onClick={() =>
          setConfirm({
            title: plan.active
              ? t('platform.plans.withdrawTitle')
              : t('platform.plans.restoreTitle'),
            message: plan.active
              ? t('platform.plans.withdrawMessage', { name: plan.name })
              : t('platform.plans.restoreMessage', { name: plan.name }),
            successMessage: plan.active
              ? t('platform.plans.withdrawDone')
              : t('platform.plans.restoreDone'),
            run: () => platformService.setPlanActive(plan.id, !plan.active),
          })
        }
        className="rounded p-1.5 text-amber-600 hover:bg-amber-50 dark:hover:bg-amber-500/10"
        title={plan.active ? t('platform.plans.withdraw') : t('platform.plans.restore')}
      >
        {plan.active ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
      </button>
      {/* Supprimer n'est proposé que sur une formule jamais souscrite : le serveur le refuse
          de toute façon, mais afficher un bouton condamné serait trompeur. */}
      {plan.subscriptionCount === 0 && (
        <button
          onClick={() =>
            setConfirm({
              title: t('platform.plans.deleteTitle'),
              message: t('platform.plans.deleteMessage', { name: plan.name }),
              successMessage: t('platform.plans.deleted'),
              run: () => platformService.deletePlan(plan.id),
            })
          }
          className="rounded p-1.5 text-red-600 hover:bg-red-50 dark:hover:bg-red-500/10"
          title={t('common.delete')}
        >
          <Trash2 className="h-4 w-4" />
        </button>
      )}
    </div>
  );

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
            {t('platform.plans.title')}
          </h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {t('platform.plans.subtitle')}
          </p>
        </div>
        <Button onClick={openCreate} icon={Plus}>
          {t('platform.plans.newPlan')}
        </Button>
      </div>

      <div className="card overflow-hidden">
        <Table columns={columns} data={plans} actions={actions} loading={loading} />
      </div>

      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? t('platform.plans.editTitle') : t('platform.plans.createTitle')}
      >
        <form onSubmit={submit} className="space-y-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FormInput
              label={t('platform.plans.code')}
              name="code"
              value={form.code}
              onChange={setField('code')}
              required
              disabled={Boolean(editing)}
              hint={editing ? t('platform.plans.codeLocked') : t('platform.plans.codeHint')}
            />
            <FormInput
              label={t('platform.plans.name')}
              name="name"
              value={form.name}
              onChange={setField('name')}
              required
            />
          </div>

          <FormInput
            label={t('common.description')}
            name="description"
            value={form.description}
            onChange={setField('description')}
          />

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FormInput
              label={t('platform.plans.monthlyPrice')}
              name="monthlyPrice"
              type="number"
              step="0.01"
              min="0"
              value={form.monthlyPrice}
              onChange={setField('monthlyPrice')}
              required
            />
            <FormInput
              label={t('platform.plans.yearlyPrice')}
              name="yearlyPrice"
              type="number"
              step="0.01"
              min="0"
              value={form.yearlyPrice}
              onChange={setField('yearlyPrice')}
              required
            />
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <FormInput
              label={t('platform.plans.maxUsers')}
              name="maxUsers"
              type="number"
              min="1"
              value={form.maxUsers}
              onChange={setField('maxUsers')}
              hint={t('platform.plans.emptyUnlimited')}
            />
            <FormInput
              label={t('platform.plans.maxProducts')}
              name="maxProducts"
              type="number"
              min="1"
              value={form.maxProducts}
              onChange={setField('maxProducts')}
              hint={t('platform.plans.emptyUnlimited')}
            />
            <FormInput
              label={t('platform.plans.trialDays')}
              name="trialDays"
              type="number"
              min="0"
              value={form.trialDays}
              onChange={setField('trialDays')}
              required
            />
          </div>

          <FormInput
            label={t('platform.plans.sortOrder')}
            name="sortOrder"
            type="number"
            value={form.sortOrder}
            onChange={setField('sortOrder')}
          />

          {/* Avertissement affiché uniquement en modification : c'est là que le piège existe. */}
          {editing && editing.subscriptionCount > 0 && (
            <p className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800 dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-300">
              {t('platform.plans.priceChangeWarning', { count: editing.subscriptionCount })}
            </p>
          )}

          <div className="flex justify-end gap-3 border-t border-gray-200 pt-4 dark:border-gray-700">
            <Button type="button" variant="secondary" onClick={() => setModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" loading={saving}>
              {editing ? t('common.saveChanges') : t('common.create')}
            </Button>
          </div>
        </form>
      </Modal>

      <ConfirmModal
        isOpen={Boolean(confirm)}
        onClose={() => setConfirm(null)}
        onConfirm={runAction}
        title={confirm?.title}
        message={confirm?.message}
      />
    </div>
  );
};

export default PlatformPlans;
