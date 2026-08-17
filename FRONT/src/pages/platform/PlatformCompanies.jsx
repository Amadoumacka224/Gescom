import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { Building2, Plus, Ban, RotateCcw, XCircle } from 'lucide-react';
import Table from '../../components/Table';
import Pagination from '../../components/Pagination';
import Modal from '../../components/Modal';
import ConfirmModal from '../../components/ConfirmModal';
import Button from '../../components/Button';
import FormInput from '../../components/FormInput';
import FormSelect from '../../components/FormSelect';
import { badgeClass } from '../../constants/statusBadges';
import { formatDate } from '../../utils/format';
import { extractErrorMessage } from '../../utils/apiError';
import platformService from '../../services/platformService';

/**
 * Parc des entreprises clientes.
 *
 * L'ouverture d'un compte crée en une seule fois l'entreprise, son administrateur initial et
 * son abonnement : le serveur traite les trois en une transaction, un compte à moitié créé
 * n'ayant aucun sens (une entreprise sans administrateur est inaccessible).
 *
 * Suspendre et résilier ne suppriment jamais rien. Le back-office n'expose d'ailleurs aucune
 * suppression d'entreprise : les données d'un client se conservent, et une réactivation doit
 * rester possible.
 */

const STATUS_TONE = {
  ACTIVE: 'success',
  TRIAL: 'info',
  SUSPENDED: 'warning',
  CANCELED: 'danger',
};

const EMPTY_FORM = {
  name: '',
  email: '',
  phone: '',
  address: '',
  city: '',
  postalCode: '',
  country: 'Belgique',
  taxId: '',
  notes: '',
  adminUsername: '',
  adminEmail: '',
  adminPassword: '',
  adminFirstName: '',
  adminLastName: '',
  planId: '',
  billingPeriod: 'MONTHLY',
  startTrial: 'true',
};

const PlatformCompanies = () => {
  const { t } = useTranslation();

  const [companies, setCompanies] = useState([]);
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [page, setPage] = useState(1); // 1-based côté UI, converti pour l'API
  const [pageSize, setPageSize] = useState(25);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [confirm, setConfirm] = useState(null);

  const loadCompanies = useCallback(async () => {
    setLoading(true);
    try {
      const response = await platformService.getCompanies({ page: page - 1, size: pageSize });
      const data = response.data;
      setCompanies(data.content ?? []);
      setTotalItems(data.totalElements ?? 0);
      setTotalPages(data.totalPages ?? 0);
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, t]);

  useEffect(() => {
    loadCompanies();
  }, [loadCompanies]);

  useEffect(() => {
    platformService
      .getPlans()
      .then((response) => setPlans(response.data ?? []))
      .catch(() => setPlans([]));
  }, []);

  const openCreate = () => {
    setEditing(null);
    setForm(EMPTY_FORM);
    setModalOpen(true);
  };

  const openEdit = (company) => {
    setEditing(company);
    setForm({
      ...EMPTY_FORM,
      name: company.name ?? '',
      email: company.email ?? '',
      phone: company.phone ?? '',
      address: company.address ?? '',
      city: company.city ?? '',
      postalCode: company.postalCode ?? '',
      country: company.country ?? 'Belgique',
      taxId: company.taxId ?? '',
      notes: company.notes ?? '',
    });
    setModalOpen(true);
  };

  const setField = (field) => (event) =>
    setForm((current) => ({ ...current, [field]: event.target.value }));

  const submit = async (event) => {
    event.preventDefault();
    setSaving(true);
    try {
      const companyPayload = {
        name: form.name,
        email: form.email,
        phone: form.phone || null,
        address: form.address || null,
        city: form.city || null,
        postalCode: form.postalCode || null,
        country: form.country || null,
        taxId: form.taxId || null,
        notes: form.notes || null,
      };

      if (editing) {
        await platformService.updateCompany(editing.id, companyPayload);
        toast.success(t('platform.companies.updated'));
      } else {
        await platformService.provisionCompany({
          company: companyPayload,
          admin: {
            username: form.adminUsername,
            email: form.adminEmail,
            password: form.adminPassword,
            firstName: form.adminFirstName,
            lastName: form.adminLastName,
          },
          planId: form.planId ? Number(form.planId) : null,
          billingPeriod: form.billingPeriod,
          startTrial: form.startTrial === 'true',
        });
        toast.success(t('platform.companies.created'));
      }
      setModalOpen(false);
      loadCompanies();
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
      loadCompanies();
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setConfirm(null);
    }
  };

  const columns = [
    {
      key: 'name',
      label: t('platform.companies.company'),
      render: (company) => (
        <div className="min-w-0">
          <p className="truncate font-medium text-gray-900 dark:text-gray-100">{company.name}</p>
          <p className="truncate text-xs text-gray-500 dark:text-gray-400">{company.email}</p>
        </div>
      ),
    },
    {
      key: 'slug',
      label: t('platform.companies.slug'),
      className: 'hidden xl:table-cell',
      render: (company) => (
        <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs dark:bg-gray-700">
          {company.slug}
        </code>
      ),
    },
    {
      key: 'subscription',
      label: t('platform.companies.plan'),
      className: 'hidden lg:table-cell',
      render: (company) =>
        company.subscription ? (
          <div>
            <p className="text-sm text-gray-900 dark:text-gray-100">
              {company.subscription.planName}
            </p>
            <p className="text-xs text-gray-500 dark:text-gray-400">
              {t(`platform.billingPeriod.${company.subscription.billingPeriod}`)}
            </p>
          </div>
        ) : (
          <span className="text-sm text-gray-400">—</span>
        ),
    },
    {
      key: 'usage',
      label: t('platform.companies.usage'),
      className: 'hidden lg:table-cell',
      render: (company) => (
        <span className="text-sm text-gray-500 dark:text-gray-400">
          {t('platform.companies.usageValue', {
            users: company.userCount,
            orders: company.orderCount,
          })}
        </span>
      ),
    },
    {
      key: 'createdAt',
      label: t('platform.companies.since'),
      className: 'hidden md:table-cell',
      render: (company) => (
        <span className="text-sm text-gray-500 dark:text-gray-400">
          {formatDate(company.createdAt)}
        </span>
      ),
    },
    {
      key: 'status',
      label: t('platform.companies.status'),
      render: (company) => (
        <span className={badgeClass(STATUS_TONE[company.status])}>
          {t(`platform.companyStatus.${company.status}`)}
        </span>
      ),
    },
  ];

  const actions = (company) => (
    <div className="flex items-center justify-end gap-1">
      <button
        onClick={() => openEdit(company)}
        className="rounded p-1.5 text-gray-500 hover:bg-gray-100 hover:text-gray-900 dark:hover:bg-gray-700 dark:hover:text-gray-100"
        title={t('common.edit')}
      >
        <Building2 className="h-4 w-4" />
      </button>

      {company.status === 'SUSPENDED' || company.status === 'CANCELED' ? (
        <button
          onClick={() =>
            setConfirm({
              title: t('platform.companies.reactivateTitle'),
              message: t('platform.companies.reactivateMessage', { name: company.name }),
              successMessage: t('platform.companies.reactivated'),
              run: () => platformService.reactivateCompany(company.id),
            })
          }
          className="rounded p-1.5 text-green-600 hover:bg-green-50 dark:hover:bg-green-500/10"
          title={t('platform.companies.reactivate')}
        >
          <RotateCcw className="h-4 w-4" />
        </button>
      ) : (
        <button
          onClick={() =>
            setConfirm({
              title: t('platform.companies.suspendTitle'),
              message: t('platform.companies.suspendMessage', { name: company.name }),
              successMessage: t('platform.companies.suspended'),
              run: () => platformService.suspendCompany(company.id, null),
            })
          }
          className="rounded p-1.5 text-amber-600 hover:bg-amber-50 dark:hover:bg-amber-500/10"
          title={t('platform.companies.suspend')}
        >
          <Ban className="h-4 w-4" />
        </button>
      )}

      {company.status !== 'CANCELED' && (
        <button
          onClick={() =>
            setConfirm({
              title: t('platform.companies.cancelTitle'),
              message: t('platform.companies.cancelMessage', { name: company.name }),
              successMessage: t('platform.companies.canceled'),
              run: () => platformService.cancelCompany(company.id, null),
            })
          }
          className="rounded p-1.5 text-red-600 hover:bg-red-50 dark:hover:bg-red-500/10"
          title={t('platform.companies.cancelAccount')}
        >
          <XCircle className="h-4 w-4" />
        </button>
      )}
    </div>
  );

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
            {t('platform.companies.title')}
          </h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {t('platform.companies.subtitle')}
          </p>
        </div>
        <Button onClick={openCreate} icon={Plus}>
          {t('platform.companies.newCompany')}
        </Button>
      </div>

      <div className="card overflow-hidden">
        <Table columns={columns} data={companies} actions={actions} loading={loading} />
        {totalPages > 0 && (
          <Pagination
            currentPage={page}
            totalPages={totalPages}
            totalItems={totalItems}
            itemsPerPage={pageSize}
            onPageChange={setPage}
            onItemsPerPageChange={(size) => {
              setPageSize(size);
              setPage(1);
            }}
          />
        )}
      </div>

      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? t('platform.companies.editTitle') : t('platform.companies.createTitle')}
        size="lg"
      >
        <form onSubmit={submit} className="space-y-5">
          <div>
            <h3 className="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-100">
              {t('platform.companies.sectionCompany')}
            </h3>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormInput
                label={t('platform.companies.name')}
                name="name"
                value={form.name}
                onChange={setField('name')}
                required
              />
              <FormInput
                label={t('common.email')}
                type="email"
                name="email"
                value={form.email}
                onChange={setField('email')}
                required
              />
              <FormInput
                label={t('common.phone')}
                name="phone"
                value={form.phone}
                onChange={setField('phone')}
              />
              <FormInput
                label={t('platform.companies.taxId')}
                name="taxId"
                value={form.taxId}
                onChange={setField('taxId')}
              />
              <FormInput
                label={t('common.address')}
                name="address"
                value={form.address}
                onChange={setField('address')}
              />
              <FormInput
                label={t('platform.companies.city')}
                name="city"
                value={form.city}
                onChange={setField('city')}
              />
              <FormInput
                label={t('platform.companies.postalCode')}
                name="postalCode"
                value={form.postalCode}
                onChange={setField('postalCode')}
              />
              <FormInput
                label={t('platform.companies.country')}
                name="country"
                value={form.country}
                onChange={setField('country')}
              />
            </div>
          </div>

          {/* Administrateur et abonnement : uniquement à la création. Modifier une entreprise
              existante ne doit pas rouvrir un compte utilisateur ni resouscrire un contrat. */}
          {!editing && (
            <>
              <div>
                <h3 className="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-100">
                  {t('platform.companies.sectionAdmin')}
                </h3>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <FormInput
                    label={t('platform.companies.adminFirstName')}
                    name="adminFirstName"
                    value={form.adminFirstName}
                    onChange={setField('adminFirstName')}
                    required
                  />
                  <FormInput
                    label={t('platform.companies.adminLastName')}
                    name="adminLastName"
                    value={form.adminLastName}
                    onChange={setField('adminLastName')}
                    required
                  />
                  <FormInput
                    label={t('platform.companies.adminUsername')}
                    name="adminUsername"
                    value={form.adminUsername}
                    onChange={setField('adminUsername')}
                    required
                  />
                  <FormInput
                    label={t('platform.companies.adminEmail')}
                    type="email"
                    name="adminEmail"
                    value={form.adminEmail}
                    onChange={setField('adminEmail')}
                    required
                  />
                  <FormInput
                    label={t('platform.companies.adminPassword')}
                    type="password"
                    name="adminPassword"
                    value={form.adminPassword}
                    onChange={setField('adminPassword')}
                    required
                  />
                </div>
              </div>

              <div>
                <h3 className="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-100">
                  {t('platform.companies.sectionSubscription')}
                </h3>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                  {/* FormSelect ajoute lui-même une première option vide : elle sert ici
                      de choix « aucune formule », l'entreprise démarrant alors sans contrat. */}
                  <FormSelect
                    label={t('platform.companies.plan')}
                    name="planId"
                    value={form.planId}
                    onChange={setField('planId')}
                    placeholder={t('platform.companies.noPlan')}
                    options={plans.map((plan) => ({ value: String(plan.id), label: plan.name }))}
                  />
                  <FormSelect
                    label={t('platform.companies.billingPeriod')}
                    name="billingPeriod"
                    value={form.billingPeriod}
                    onChange={setField('billingPeriod')}
                    allowEmpty={false}
                    options={[
                      { value: 'MONTHLY', label: t('platform.billingPeriod.MONTHLY') },
                      { value: 'YEARLY', label: t('platform.billingPeriod.YEARLY') },
                    ]}
                  />
                  <FormSelect
                    label={t('platform.companies.startMode')}
                    name="startTrial"
                    value={form.startTrial}
                    onChange={setField('startTrial')}
                    allowEmpty={false}
                    options={[
                      { value: 'true', label: t('platform.companies.startTrial') },
                      { value: 'false', label: t('platform.companies.startPaid') },
                    ]}
                  />
                </div>
              </div>
            </>
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

export default PlatformCompanies;
