import { useEffect, useState } from 'react';
import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../context/useAuth';
import platformService from '../services/platformService';
import {
  Gauge,
  Building2,
  Users,
  BadgeEuro,
  Layers,
  Receipt,
  Activity,
  Settings,
  LifeBuoy,
  Bell,
  LogOut,
  ChevronLeft,
  ShieldCheck,
} from 'lucide-react';
import { motion } from 'framer-motion';

/* Barre latérale du back-office propriétaire.
 *
 * Volontairement distincte de `Sidebar` plutôt que pilotée par un rôle de plus : les deux
 * espaces n'ont aucun lien en commun, et la teinte ardoise signale d'emblée que l'on n'est
 * pas dans l'application métier. Confondre les deux serait la meilleure façon de croire
 * qu'on administre une entreprise alors qu'on exploite la plateforme entière. */

const NAV_ITEMS = [
  { path: '/platform', icon: Gauge, labelKey: 'platform.nav.dashboard', end: true },
  { path: '/platform/companies', icon: Building2, labelKey: 'platform.nav.companies' },
  { path: '/platform/users', icon: Users, labelKey: 'platform.nav.users' },
  { path: '/platform/subscriptions', icon: BadgeEuro, labelKey: 'platform.nav.subscriptions' },
  { path: '/platform/plans', icon: Layers, labelKey: 'platform.nav.plans' },
  { path: '/platform/payments', icon: Receipt, labelKey: 'platform.nav.payments' },
  { path: '/platform/support', icon: LifeBuoy, labelKey: 'platform.nav.support', badge: 'support' },
  {
    path: '/platform/notifications',
    icon: Bell,
    labelKey: 'platform.nav.notifications',
    badge: 'notifications',
  },
  { path: '/platform/activity', icon: Activity, labelKey: 'platform.nav.activity' },
  { path: '/platform/settings', icon: Settings, labelKey: 'platform.nav.settings' },
];

const navLinkClass = ({ isActive }) =>
  `group relative flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors duration-150 outline-none focus-visible:ring-2 focus-visible:ring-slate-400/50 ${
    isActive
      ? 'bg-slate-800 font-semibold text-white'
      : 'font-medium text-slate-400 hover:bg-slate-800/60 hover:text-slate-100'
  }`;

const NavItem = ({ to, icon: Icon, label, end, count, onClick }) => (
  <NavLink to={to} end={end} onClick={onClick} className={navLinkClass}>
    {({ isActive }) => (
      <>
        {isActive && (
          <span className="absolute -left-5 top-1/2 h-5 w-1 -translate-y-1/2 rounded-r-full bg-primary-400" />
        )}
        <Icon
          className={`h-[18px] w-[18px] shrink-0 ${
            isActive ? 'text-primary-400' : 'text-slate-500 group-hover:text-slate-300'
          }`}
          strokeWidth={2}
        />
        <span className="truncate">{label}</span>
        {/* Le compteur n'apparaît qu'à partir de 1 : un « 0 » permanent devient du bruit
            et fait perdre au badge sa valeur d'alerte. */}
        {count > 0 && (
          <span className="ml-auto shrink-0 rounded-full bg-primary-500 px-1.5 py-0.5 text-[10px] font-semibold tabular-nums text-white">
            {count > 99 ? '99+' : count}
          </span>
        )}
      </>
    )}
  </NavLink>
);

const PlatformSidebar = ({ isOpen, setIsOpen }) => {
  const { t } = useTranslation();
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [counts, setCounts] = useState({ support: 0, notifications: 0 });

  /* Compteurs rafraîchis à chaque changement d'écran plutôt que par un intervalle : le
   * back-office n'a qu'un utilisateur, la navigation est le seul moment où l'état a pu
   * changer de son fait. Un sondage périodique interrogerait la base sans rien apprendre.
   * Les échecs sont ignorés — un badge absent ne justifie pas un message d'erreur. */
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      platformService.getOpenTicketCount().catch(() => null),
      platformService.getUnreadCount().catch(() => null),
    ]).then(([tickets, unread]) => {
      if (cancelled) return;
      setCounts({
        support: tickets?.data?.count ?? 0,
        notifications: unread?.data?.count ?? 0,
      });
    });
    return () => {
      cancelled = true;
    };
  }, [location.pathname]);

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
        <ShieldCheck className="h-5 w-5 text-white" />
      </div>
      <div className="min-w-0">
        <p className="truncate text-[15px] font-bold leading-tight tracking-tight text-white">
          {t('platform.brand')}
        </p>
        <p className="truncate text-[11px] leading-tight text-slate-400">
          {t('platform.tagline')}
        </p>
      </div>
    </div>
  );

  const renderNav = (onItemClick) => (
    <nav aria-label={t('platform.brand')} className="flex-1 space-y-1 overflow-y-auto px-5 py-5">
      {NAV_ITEMS.map((item) => (
        <NavItem
          key={item.path}
          to={item.path}
          end={item.end}
          icon={item.icon}
          label={t(item.labelKey)}
          count={item.badge ? counts[item.badge] : 0}
          onClick={onItemClick}
        />
      ))}
    </nav>
  );

  const renderFooter = () => (
    <div className="border-t border-slate-800 px-5 py-4">
      <div className="mb-2 flex items-center gap-3 rounded-xl bg-slate-800/60 px-3 py-2.5">
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary-600 shadow-soft">
          <span className="text-xs font-semibold text-white">{initial}</span>
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold leading-tight text-slate-100">{fullName}</p>
          <p className="truncate text-[11px] uppercase tracking-wide text-slate-400">
            {t('platform.roleLabel')}
          </p>
        </div>
      </div>

      <button
        onClick={handleLogout}
        className="group flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-slate-400 outline-none transition-colors duration-150 hover:bg-red-500/10 hover:text-red-400 focus-visible:ring-2 focus-visible:ring-red-500/40"
      >
        <LogOut
          className="h-[18px] w-[18px] shrink-0 text-slate-500 group-hover:text-red-400"
          strokeWidth={2}
        />
        <span>{t('auth.logout')}</span>
      </button>
    </div>
  );

  return (
    <>
      {isOpen && (
        <div
          className="fixed inset-0 z-40 bg-gray-900/60 backdrop-blur-sm lg:hidden"
          onClick={() => setIsOpen(false)}
        />
      )}

      <aside className="z-30 hidden border-r border-slate-800 bg-slate-900 lg:inset-y-0 lg:left-0 lg:fixed lg:flex lg:w-64 lg:flex-col">
        <div className="flex h-16 shrink-0 items-center border-b border-slate-800 px-5">
          {renderBrand()}
        </div>
        {renderNav()}
        {renderFooter()}
      </aside>

      <motion.aside
        initial={false}
        animate={{ x: isOpen ? 0 : -272 }}
        className="fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-slate-800 bg-slate-900 shadow-elevated lg:hidden"
      >
        <div className="flex h-16 shrink-0 items-center justify-between gap-2 border-b border-slate-800 px-5">
          {renderBrand()}
          <button
            onClick={() => setIsOpen(false)}
            aria-label={t('nav.closeMenu')}
            className="shrink-0 rounded-lg p-2 text-slate-400 hover:bg-slate-800"
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
        </div>
        {renderNav(() => setIsOpen(false))}
        {renderFooter()}
      </motion.aside>
    </>
  );
};

export default PlatformSidebar;
