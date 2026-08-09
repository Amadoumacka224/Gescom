import { NavLink, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../context/useAuth';
import {
  LayoutDashboard,
  BarChart3,
  Banknote,
  CreditCard,
  ShoppingCart,
  FileText,
  Truck,
  Users,
  Package,
  Tags,
  Warehouse,
  UserCog,
  History,
  Settings,
  CircleUser,
  LogOut,
  ChevronLeft,
} from 'lucide-react';
import { motion } from 'framer-motion';

/* Barre latérale : marque en tête, navigation groupée par domaine métier au centre,
 * compte utilisateur en pied. Les sections suivent le parcours de l'application
 * (pilotage → vente → catalogue → administration) et non l'ordre des routes ;
 * chacune n'apparaît que si le rôle courant a accès à au moins un de ses liens. */

// Sections de navigation. `roles` filtre lien par lien : une section vide est masquée.
const NAV_SECTIONS = [
  {
    id: 'overview',
    items: [
      { path: '/dashboard', icon: LayoutDashboard, labelKey: 'nav.dashboard', roles: ['ADMIN'] },
      { path: '/reports', icon: BarChart3, labelKey: 'nav.reports', roles: ['ADMIN'] },
      { path: '/caisses', icon: Banknote, labelKey: 'nav.caisses', roles: ['ADMIN'] },
    ],
  },
  {
    id: 'sales',
    items: [
      { path: '/caisse', icon: CreditCard, labelKey: 'nav.caisse', roles: ['CAISSIER'] },
      // Ordre du cycle de vie d'une commande : commande → facture → livraison.
      { path: '/orders', icon: ShoppingCart, labelKey: 'nav.orders', roles: ['ADMIN', 'CAISSIER'] },
      { path: '/invoices', icon: FileText, labelKey: 'nav.invoices', roles: ['ADMIN', 'CAISSIER'] },
      { path: '/deliveries', icon: Truck, labelKey: 'nav.deliveries', roles: ['ADMIN', 'CAISSIER'] },
      { path: '/clients', icon: Users, labelKey: 'nav.clients', roles: ['ADMIN', 'CAISSIER'] },
    ],
  },
  {
    id: 'catalog',
    items: [
      { path: '/products', icon: Package, labelKey: 'nav.products', roles: ['ADMIN', 'CAISSIER'] },
      { path: '/categories', icon: Tags, labelKey: 'nav.categories', roles: ['ADMIN', 'CAISSIER'] },
      { path: '/stock', icon: Warehouse, labelKey: 'nav.stock', roles: ['ADMIN'] },
    ],
  },
  {
    id: 'admin',
    items: [
      { path: '/users', icon: UserCog, labelKey: 'nav.users', roles: ['ADMIN'] },
      { path: '/history', icon: History, labelKey: 'nav.history', roles: ['ADMIN'] },
      { path: '/settings', icon: Settings, labelKey: 'nav.settings', roles: ['ADMIN'] },
    ],
  },
];

// Lien de navigation : accent latéral à l'état actif, survol et focus clavier cohérents.
const navLinkClass = ({ isActive }) =>
  `group relative flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors duration-150 outline-none focus-visible:ring-2 focus-visible:ring-primary-500/50 ${
    isActive
      ? 'bg-primary-50 font-semibold text-primary-700 dark:bg-primary-500/10 dark:text-primary-200'
      : 'font-medium text-gray-600 hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-700/50 dark:hover:text-gray-100'
  }`;

const NavItem = ({ to, icon: Icon, label, onClick }) => (
  <NavLink to={to} onClick={onClick} className={navLinkClass}>
    {({ isActive }) => (
      <>
        {isActive && (
          // Accent aligné sur le bord de la barre (compense le px-5 du conteneur).
          <span className="absolute -left-5 top-1/2 h-5 w-1 -translate-y-1/2 rounded-r-full bg-primary-600 dark:bg-primary-400" />
        )}
        <Icon
          className={`h-[18px] w-[18px] shrink-0 ${
            isActive ? 'text-primary-600 dark:text-primary-300' : 'text-gray-400 group-hover:text-gray-600 dark:group-hover:text-gray-200'
          }`}
          strokeWidth={2}
        />
        <span className="truncate">{label}</span>
      </>
    )}
  </NavLink>
);

const Sidebar = ({ isOpen, setIsOpen }) => {
  const { t } = useTranslation();
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const sections = NAV_SECTIONS.map((section) => ({
    ...section,
    items: section.items.filter((item) => item.roles.includes(user?.role)),
  })).filter((section) => section.items.length > 0);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const initial =
    user?.firstName?.charAt(0).toUpperCase() || user?.username?.charAt(0).toUpperCase() || '?';

  const fullName =
    user?.firstName && user?.lastName ? `${user.firstName} ${user.lastName}` : user?.username;

  const renderBrand = () => (
    <div className="flex min-w-0 items-center gap-3">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-primary-500 to-primary-700 shadow-soft">
        <LayoutDashboard className="h-5 w-5 text-white" />
      </div>
      <div className="min-w-0">
        <p className="truncate text-[15px] font-bold leading-tight tracking-tight text-gray-900 dark:text-gray-100">
          {t('app.name')}
        </p>
        <p className="truncate text-[11px] leading-tight text-gray-500 dark:text-gray-400">
          {t('app.tagline')}
        </p>
      </div>
    </div>
  );

  const renderNav = (onItemClick) => (
    <nav aria-label={t('app.name')} className="flex-1 space-y-6 overflow-y-auto px-5 py-5">
      {sections.map((section) => (
        <div key={section.id} className="space-y-1">
          <p className="px-3 pb-1 text-[11px] font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
            {t(`nav.sections.${section.id}`)}
          </p>
          {section.items.map((item) => (
            <NavItem
              key={item.path}
              to={item.path}
              icon={item.icon}
              label={t(item.labelKey)}
              onClick={onItemClick}
            />
          ))}
        </div>
      ))}
    </nav>
  );

  // Pied : carte compte + profil + déconnexion, à l'emplacement attendu dans un back-office.
  const renderFooter = (onItemClick) => (
    <div className="border-t border-gray-200 px-5 py-4 dark:border-gray-700">
      <div className="mb-2 flex items-center gap-3 rounded-xl bg-gray-50 px-3 py-2.5 dark:bg-gray-700/40">
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary-600 shadow-soft">
          <span className="text-xs font-semibold text-white">{initial}</span>
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold leading-tight text-gray-900 dark:text-gray-100">
            {fullName}
          </p>
          <p className="truncate text-[11px] uppercase tracking-wide text-gray-500 dark:text-gray-400">
            {user?.role}
          </p>
        </div>
      </div>

      <div className="space-y-1">
        <NavItem
          to="/profile"
          icon={CircleUser}
          label={t('nav.profile')}
          onClick={onItemClick}
        />
        <button
          onClick={handleLogout}
          className="group flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-gray-600 outline-none transition-colors duration-150 hover:bg-red-50 hover:text-red-600 focus-visible:ring-2 focus-visible:ring-red-500/40 dark:text-gray-400 dark:hover:bg-red-500/10 dark:hover:text-red-400"
        >
          <LogOut
            className="h-[18px] w-[18px] shrink-0 text-gray-400 group-hover:text-red-600 dark:group-hover:text-red-400"
            strokeWidth={2}
          />
          <span>{t('auth.logout')}</span>
        </button>
      </div>
    </div>
  );

  return (
    <>
      {/* Overlay mobile */}
      {isOpen && (
        <div
          className="fixed inset-0 z-40 bg-gray-900/50 backdrop-blur-sm lg:hidden"
          onClick={() => setIsOpen(false)}
        />
      )}

      {/* Sidebar desktop — toujours visible à partir de lg */}
      <aside className="z-30 hidden border-r border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800 lg:inset-y-0 lg:left-0 lg:fixed lg:flex lg:w-64 lg:flex-col">
        <div className="flex h-16 shrink-0 items-center border-b border-gray-200 px-5 dark:border-gray-700">
          {renderBrand()}
        </div>
        {renderNav()}
        {renderFooter()}
      </aside>

      {/* Sidebar mobile */}
      <motion.aside
        initial={false}
        animate={{ x: isOpen ? 0 : -272 }}
        className="fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-gray-200 bg-white shadow-elevated dark:border-gray-700 dark:bg-gray-800 lg:hidden"
      >
        <div className="flex h-16 shrink-0 items-center justify-between gap-2 border-b border-gray-200 px-5 dark:border-gray-700">
          {renderBrand()}
          <button
            onClick={() => setIsOpen(false)}
            aria-label={t('nav.closeMenu')}
            className="shrink-0 rounded-lg p-2 text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700"
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
        </div>
        {renderNav(() => setIsOpen(false))}
        {renderFooter(() => setIsOpen(false))}
      </motion.aside>
    </>
  );
};

export default Sidebar;
