import { useMemo } from 'react';
import { Search, SlidersHorizontal, X, RotateCcw, Calendar } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import SearchableSelect from './SearchableSelect';

/**
 * Barre de recherche et de filtrage des commandes.
 *
 * Organisation en trois étages, du plus utilisé au plus rare — c'est ce qui évite le mur de
 * champs : une recherche plein texte toujours visible, un panneau de critères précis replié
 * par défaut, et une rangée de pastilles rappelant ce qui est actif. Sans ces pastilles, un
 * filtre oublié dans un panneau fermé fait passer une liste tronquée pour la liste complète —
 * c'est le principal piège des écrans de ce genre.
 *
 * Le composant ne détient aucun état : il reçoit `filters` et remonte chaque changement par
 * `onChange(champ, valeur)`. Le filtrage lui-même est appliqué par la page, qui seule connaît
 * les commandes.
 *
 * Les listes d'options ne portent que des clés : les libellés de statut viennent de la table
 * canonique `status.*`, partagée avec les badges, pour qu'un même statut se lise partout pareil.
 */

const STATUS_OPTIONS = [
  { value: 'ALL', labelKey: 'orders.filters.allStatuses' },
  { value: 'PENDING', labelKey: 'status.order.PENDING' },
  { value: 'CONFIRMED', labelKey: 'status.order.CONFIRMED' },
  { value: 'INVOICED', labelKey: 'status.order.INVOICED' },
  { value: 'DELIVERED', labelKey: 'status.order.DELIVERED' },
  { value: 'CANCELED', labelKey: 'status.order.CANCELED' },
];

const PAYMENT_OPTIONS = [
  { value: 'ALL', labelKey: 'orders.filters.allPayments' },
  { value: 'NONE', labelKey: 'orders.filters.notInvoicedYet' },
  { value: 'UNPAID', labelKey: 'status.invoice.UNPAID' },
  { value: 'PARTIALLY_PAID', labelKey: 'status.invoice.PARTIALLY_PAID' },
  { value: 'PAID', labelKey: 'status.invoice.PAID' },
  { value: 'CANCELED', labelKey: 'orders.filters.invoiceCanceled' },
];

const CLIENT_TYPE_OPTIONS = [
  { value: 'ALL', labelKey: 'orders.filters.allClientTypes' },
  { value: 'PARTICULIER', labelKey: 'clients.typeIndividual' },
  { value: 'ENTREPRISE', labelKey: 'clients.typeCompany' },
];

/** `input type="date"` attend un `yyyy-MM-dd` : on formate en local, pas en UTC. */
const toDateInput = (date) => {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
};

/**
 * Raccourcis de période. Ils écrivent dans les deux mêmes champs que la saisie manuelle
 * plutôt que de créer un troisième état : un raccourci reste donc modifiable à la main,
 * et les pastilles n'ont qu'un seul couple de valeurs à décrire.
 */
const buildDateShortcuts = () => {
  const today = new Date();
  const startOfDay = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const shift = (days) => {
    const d = startOfDay(today);
    d.setDate(d.getDate() - days);
    return d;
  };
  const startOfMonth = (offset = 0) => new Date(today.getFullYear(), today.getMonth() + offset, 1);
  const endOfMonth = (offset = 0) => new Date(today.getFullYear(), today.getMonth() + offset + 1, 0);

  return [
    { key: 'today', labelKey: 'filters.range.today', from: startOfDay(today), to: startOfDay(today) },
    { key: '7d', labelKey: 'filters.range.last7', from: shift(6), to: startOfDay(today) },
    { key: '30d', labelKey: 'filters.range.last30', from: shift(29), to: startOfDay(today) },
    { key: 'month', labelKey: 'filters.range.thisMonth', from: startOfMonth(), to: endOfMonth() },
    { key: 'prevMonth', labelKey: 'filters.range.lastMonth', from: startOfMonth(-1), to: endOfMonth(-1) },
  ];
};

const Field = ({ label, children, htmlFor }) => (
  <div>
    <label
      htmlFor={htmlFor}
      className="block text-xs font-semibold text-gray-600 dark:text-gray-400 mb-1.5"
    >
      {label}
    </label>
    {children}
  </div>
);

const Chip = ({ label, removeLabel, onRemove }) => (
  <span className="inline-flex items-center gap-1.5 pl-2.5 pr-1.5 py-1 rounded-full bg-primary-100 dark:bg-primary-500/20 text-primary-900 dark:text-primary-100 text-xs font-medium">
    {label}
    <button
      type="button"
      onClick={onRemove}
      aria-label={removeLabel}
      className="p-0.5 rounded-full hover:bg-primary-300/60 dark:hover:bg-primary-400/30 transition-colors"
    >
      <X className="w-3 h-3" />
    </button>
  </span>
);

const OrderFilters = ({
  filters,
  onChange,
  onReset,
  expanded,
  onToggleExpanded,
  clients = [],
  products = [],
  categories = [],
  users = [],
  cities = [],
}) => {
  const { t } = useTranslation();

  // Calculés une fois : les bornes dépendent de la date du jour, pas des props.
  const shortcuts = useMemo(() => buildDateShortcuts(), []);
  const selectClass =
    'w-full px-3 py-2 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-lg text-sm text-gray-900 dark:text-gray-100 focus:border-primary-500 focus:ring-4 focus:ring-primary-500/15 outline-none transition-all';
  const comboClass =
    'w-full pl-9 pr-8 py-2 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-lg text-sm text-gray-900 dark:text-gray-100 focus:border-primary-500 focus:ring-4 focus:ring-primary-500/15 outline-none transition-all';

  /** Libellé traduit d'une option, pour les pastilles de filtres actifs. */
  const labelFrom = (options, value) => {
    const option = options.find((o) => o.value === value);
    return option ? t(option.labelKey) : value;
  };

  /* Pastilles : une entrée par critère renseigné, avec de quoi le retirer seul. `q` en est
   * exclu — il reste visible dans son champ, une pastille ne ferait que le redire. */
  const chips = [];
  const push = (field, label, resetTo = '') => chips.push({ field, label, resetTo });

  if (filters.status !== 'ALL') push('status', labelFrom(STATUS_OPTIONS, filters.status), 'ALL');
  if (filters.payment !== 'ALL') push('payment', labelFrom(PAYMENT_OPTIONS, filters.payment), 'ALL');
  if (filters.clientId) {
    const c = clients.find((x) => String(x.id) === String(filters.clientId));
    push('clientId', t('orders.filters.chipClient', {
      value: c ? `${c.firstName} ${c.lastName}` : filters.clientId,
    }));
  }
  if (filters.clientType !== 'ALL') {
    push('clientType', t('orders.filters.chipClientType', {
      value: labelFrom(CLIENT_TYPE_OPTIONS, filters.clientType),
    }), 'ALL');
  }
  if (filters.city) push('city', t('orders.filters.chipCity', { value: filters.city }));
  if (filters.productId) {
    const p = products.find((x) => String(x.id) === String(filters.productId));
    push('productId', t('orders.filters.chipProduct', { value: p?.name ?? filters.productId }));
  }
  if (filters.categoryId) {
    const c = categories.find((x) => String(x.id) === String(filters.categoryId));
    push('categoryId', t('orders.filters.chipCategory', { value: c?.name ?? filters.categoryId }));
  }
  if (filters.createdById) {
    const u = users.find((x) => String(x.id) === String(filters.createdById));
    push('createdById', t('orders.filters.chipCreatedBy', {
      value: u?.label ?? filters.createdById,
    }));
  }
  if (filters.dateFrom) push('dateFrom', t('orders.filters.chipFrom', { value: filters.dateFrom }));
  if (filters.dateTo) push('dateTo', t('orders.filters.chipTo', { value: filters.dateTo }));
  if (filters.amountMin) push('amountMin', t('orders.filters.chipAmountMin', { value: filters.amountMin }));
  if (filters.amountMax) push('amountMax', t('orders.filters.chipAmountMax', { value: filters.amountMax }));
  if (filters.notes) push('notes', t('orders.filters.chipNotes', { value: filters.notes }));
  if (filters.onlyDiscounted) push('onlyDiscounted', t('orders.filters.chipDiscounted'), false);

  const activeCount = chips.length + (filters.q ? 1 : 0);

  const applyShortcut = (s) => {
    onChange('dateFrom', toDateInput(s.from));
    onChange('dateTo', toDateInput(s.to));
  };

  const shortcutActive = (s) =>
    filters.dateFrom === toDateInput(s.from) && filters.dateTo === toDateInput(s.to);

  return (
    <section
      className="bg-white dark:bg-gray-800 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-card"
      aria-label={t('orders.filters.regionLabel')}
    >
      {/* Étage 1 — recherche plein texte, toujours accessible */}
      <div className="p-4 flex flex-col lg:flex-row lg:items-center gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 pointer-events-none" />
          <input
            id="order-search"
            type="search"
            value={filters.q}
            onChange={(e) => onChange('q', e.target.value)}
            placeholder={t('orders.filters.searchPlaceholder')}
            className="w-full pl-10 pr-4 py-2.5 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-lg text-sm text-gray-900 dark:text-gray-100 placeholder:text-gray-400 focus:border-primary-500 focus:ring-4 focus:ring-primary-500/15 outline-none transition-all"
          />
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onToggleExpanded}
            aria-expanded={expanded}
            aria-controls="order-advanced-filters"
            className="btn-secondary text-sm whitespace-nowrap"
          >
            <SlidersHorizontal className="w-4 h-4" />
            {t('filters.advanced')}
            {activeCount > 0 && (
              <span className="ml-1 min-w-[1.25rem] px-1.5 py-0.5 rounded-full bg-primary-600 text-white text-[11px] font-bold tabular-nums">
                {activeCount}
              </span>
            )}
          </button>
          {activeCount > 0 && (
            <button
              type="button"
              onClick={onReset}
              className="btn-secondary text-sm whitespace-nowrap"
              title={t('filters.resetAllTooltip')}
            >
              <RotateCcw className="w-4 h-4" />
              {t('filters.reset')}
            </button>
          )}
        </div>
      </div>

      {/* Étage 2 — critères précis, repliés par défaut */}
      {expanded && (
        <div
          id="order-advanced-filters"
          className="px-4 pb-4 border-t border-gray-200 dark:border-gray-700 pt-4 space-y-4"
        >
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <Field label={t('orders.filters.statusLabel')} htmlFor="f-status">
              <select
                id="f-status"
                value={filters.status}
                onChange={(e) => onChange('status', e.target.value)}
                className={selectClass}
              >
                {STATUS_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{t(o.labelKey)}</option>
                ))}
              </select>
            </Field>

            <Field label={t('orders.filters.paymentLabel')} htmlFor="f-payment">
              <select
                id="f-payment"
                value={filters.payment}
                onChange={(e) => onChange('payment', e.target.value)}
                className={selectClass}
              >
                {PAYMENT_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{t(o.labelKey)}</option>
                ))}
              </select>
            </Field>

            <Field label={t('orders.client')}>
              <SearchableSelect
                options={clients}
                value={filters.clientId}
                onChange={(v) => onChange('clientId', v)}
                getOptionValue={(c) => c.id}
                getOptionLabel={(c) => `${c.firstName} ${c.lastName}`}
                getOptionSearch={(c) => `${c.email || ''} ${c.phone || ''} ${c.company || ''}`}
                placeholder={t('orders.filters.allClients')}
                inputClassName={comboClass}
              />
            </Field>

            <Field label={t('orders.filters.clientTypeLabel')} htmlFor="f-client-type">
              <select
                id="f-client-type"
                value={filters.clientType}
                onChange={(e) => onChange('clientType', e.target.value)}
                className={selectClass}
              >
                {CLIENT_TYPE_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{t(o.labelKey)}</option>
                ))}
              </select>
            </Field>

            <Field label={t('orders.filters.containsProduct')}>
              <SearchableSelect
                options={products}
                value={filters.productId}
                onChange={(v) => onChange('productId', v)}
                getOptionValue={(p) => p.id}
                getOptionLabel={(p) => p.name}
                getOptionSearch={(p) => `${p.code || ''} ${p.barcode || ''}`}
                placeholder={t('orders.filters.allProducts')}
                inputClassName={comboClass}
              />
            </Field>

            <Field label={t('orders.filters.categoryLabel')} htmlFor="f-category">
              <select
                id="f-category"
                value={filters.categoryId}
                onChange={(e) => onChange('categoryId', e.target.value)}
                className={selectClass}
              >
                <option value="">{t('orders.filters.allCategories')}</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            </Field>

            <Field label={t('orders.filters.createdByLabel')} htmlFor="f-user">
              <select
                id="f-user"
                value={filters.createdById}
                onChange={(e) => onChange('createdById', e.target.value)}
                className={selectClass}
              >
                <option value="">{t('orders.filters.allUsers')}</option>
                {users.map((u) => (
                  <option key={u.id} value={u.id}>{u.label}</option>
                ))}
              </select>
            </Field>

            <Field label={t('orders.filters.cityLabel')} htmlFor="f-city">
              <select
                id="f-city"
                value={filters.city}
                onChange={(e) => onChange('city', e.target.value)}
                className={selectClass}
              >
                <option value="">{t('orders.filters.allCities')}</option>
                {cities.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </Field>

            <Field label={t('orders.filters.orderedFrom')} htmlFor="f-date-from">
              <input
                id="f-date-from"
                type="date"
                value={filters.dateFrom}
                onChange={(e) => onChange('dateFrom', e.target.value)}
                className={selectClass}
              />
            </Field>

            <Field label={t('orders.filters.orderedUntil')} htmlFor="f-date-to">
              <input
                id="f-date-to"
                type="date"
                value={filters.dateTo}
                onChange={(e) => onChange('dateTo', e.target.value)}
                className={selectClass}
              />
            </Field>

            <Field label={t('orders.filters.amountMinLabel')} htmlFor="f-amount-min">
              <input
                id="f-amount-min"
                type="number"
                min="0"
                step="0.01"
                inputMode="decimal"
                value={filters.amountMin}
                onChange={(e) => onChange('amountMin', e.target.value)}
                placeholder={t('orders.filters.amountMinPlaceholder')}
                className={selectClass}
              />
            </Field>

            <Field label={t('orders.filters.amountMaxLabel')} htmlFor="f-amount-max">
              <input
                id="f-amount-max"
                type="number"
                min="0"
                step="0.01"
                inputMode="decimal"
                value={filters.amountMax}
                onChange={(e) => onChange('amountMax', e.target.value)}
                placeholder={t('orders.filters.amountMaxPlaceholder')}
                className={selectClass}
              />
            </Field>

            <Field label={t('orders.filters.notesLabel')} htmlFor="f-notes">
              <input
                id="f-notes"
                type="text"
                value={filters.notes}
                onChange={(e) => onChange('notes', e.target.value)}
                placeholder={t('orders.filters.notesPlaceholder')}
                className={selectClass}
              />
            </Field>

            <div className="flex items-end">
              <label className="inline-flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300 cursor-pointer py-2">
                <input
                  type="checkbox"
                  checked={filters.onlyDiscounted}
                  onChange={(e) => onChange('onlyDiscounted', e.target.checked)}
                  className="w-4 h-4 rounded border-gray-300 dark:border-gray-600 text-primary-600 focus:ring-primary-500/40"
                />
                {t('orders.filters.onlyDiscounted')}
              </label>
            </div>
          </div>

          {/* Raccourcis de période : ils écrivent dans les deux champs de date ci-dessus */}
          <div className="flex flex-wrap items-center gap-2 pt-1">
            <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-gray-600 dark:text-gray-400">
              <Calendar className="w-3.5 h-3.5" />
              {t('filters.period')}
            </span>
            {shortcuts.map((s) => (
              <button
                key={s.key}
                type="button"
                onClick={() => applyShortcut(s)}
                aria-pressed={shortcutActive(s)}
                className={`px-2.5 py-1 rounded-full text-xs font-medium border transition-colors ${
                  shortcutActive(s)
                    ? 'bg-primary-600 border-primary-600 text-white'
                    : 'bg-white dark:bg-gray-700 border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 hover:border-primary-400'
                }`}
              >
                {t(s.labelKey)}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Étage 3 — rappel de ce qui filtre réellement la liste affichée */}
      {chips.length > 0 && (
        <div className="px-4 py-3 border-t border-gray-200 dark:border-gray-700 flex flex-wrap items-center gap-2">
          <span className="text-xs font-semibold text-gray-500 dark:text-gray-400 mr-1">
            {t('filters.active')}
          </span>
          {chips.map((c) => (
            <Chip
              key={c.field}
              label={c.label}
              removeLabel={t('filters.removeFilter', { label: c.label })}
              onRemove={() => onChange(c.field, c.resetTo)}
            />
          ))}
        </div>
      )}

    </section>
  );
};

export default OrderFilters;
