import { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Menu, Bell, Globe, Search, ShoppingCart, AlertTriangle, PackageX } from 'lucide-react';
import { useNotifications } from '../hooks/useNotifications';

const Header = ({ onMenuClick }) => {
  const { t, i18n } = useTranslation();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [showNotifications, setShowNotifications] = useState(false);
  const { notifications, unreadCount, markAllRead } = useNotifications(user);
  const notifRef = useRef(null);

  // Fermeture du panneau au clic en dehors.
  useEffect(() => {
    const onClickOutside = (e) => {
      if (notifRef.current && !notifRef.current.contains(e.target)) {
        setShowNotifications(false);
      }
    };
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  const toggleNotifications = () => {
    const next = !showNotifications;
    setShowNotifications(next);
    if (next) markAllRead(); // consulter = marquer comme lu
  };

  // Temps relatif localisé (« il y a 5 minutes » / « 5 minutes ago »…) sans dépendance.
  const relTime = (iso) => {
    if (!iso) return '';
    const rtf = new Intl.RelativeTimeFormat(i18n.language, { numeric: 'auto' });
    const mins = Math.round((new Date(iso) - new Date()) / 60000);
    if (Math.abs(mins) < 60) return rtf.format(mins, 'minute');
    const hrs = Math.round(mins / 60);
    if (Math.abs(hrs) < 24) return rtf.format(hrs, 'hour');
    return rtf.format(Math.round(hrs / 24), 'day');
  };

  const notifTitle = (n) => {
    if (n.kind === 'order') return t('notifications.orderToConfirm', { number: n.data.number });
    if (n.kind === 'stock-out') return t('notifications.outOfStock', { product: n.data.product });
    return t('notifications.lowStock', { product: n.data.product });
  };

  const notifSubtitle = (n) => {
    if (n.kind === 'order') {
      return [t('notifications.orderClient', { client: n.data.client }), relTime(n.time)]
        .filter(Boolean)
        .join(' · ');
    }
    return t('notifications.stockLevel', { qty: n.data.qty, threshold: n.data.threshold });
  };

  const notifIcon = (kind) => {
    if (kind === 'order') return <ShoppingCart className="w-4 h-4 text-blue-600" />;
    if (kind === 'stock-out') return <PackageX className="w-4 h-4 text-red-600" />;
    return <AlertTriangle className="w-4 h-4 text-amber-600" />;
  };

  const onNotifClick = (n) => {
    setShowNotifications(false);
    navigate(n.link);
  };

  const toggleLanguage = () => {
    const languages = ['fr', 'en', 'nl'];
    const currentIndex = languages.indexOf(i18n.language);
    const nextIndex = (currentIndex + 1) % languages.length;
    const newLang = languages[nextIndex];
    i18n.changeLanguage(newLang);
    localStorage.setItem('language', newLang);
  };

  return (
    <header className="bg-white/80 dark:bg-gray-800/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-700 sticky top-0 z-30">
      <div className="px-4 sm:px-6 h-16 flex items-center justify-between">
        {/* Left Section */}
        <div className="flex items-center gap-4">
          <button
            onClick={onMenuClick}
            aria-label={t('nav.openMenu')}
            className="lg:hidden p-2 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
          >
            <Menu className="w-6 h-6" />
          </button>

          {/* Search Bar */}
          <div className="hidden md:flex items-center gap-2 bg-gray-100 dark:bg-gray-700/60 rounded-lg px-3.5 py-2 w-96 ring-1 ring-transparent focus-within:ring-primary-500/40 focus-within:bg-white dark:focus-within:bg-gray-700 transition-all">
            <Search className="w-4 h-4 text-gray-400" />
            <input
              type="text"
              placeholder={t('common.search') + '...'}
              className="bg-transparent outline-none text-sm flex-1 text-gray-700 dark:text-gray-200 placeholder:text-gray-400"
            />
          </div>
        </div>

        {/* Right Section */}
        <div className="flex items-center gap-1.5 sm:gap-2">
          {/* Language Toggle */}
          <button
            onClick={toggleLanguage}
            className="flex items-center gap-2 px-3 py-2 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
            title={t('common.changeLanguage')}
          >
            <Globe className="w-5 h-5" />
            <span className="text-sm font-medium uppercase">
              {i18n.language}
            </span>
          </button>

          {/* Notifications */}
          <div className="relative" ref={notifRef}>
            <button
              onClick={toggleNotifications}
              aria-label={t('notifications.title')}
              className="relative p-2 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
            >
              <Bell className="w-5 h-5" />
              {unreadCount > 0 && (
                <span className="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] px-1 flex items-center justify-center text-[10px] font-bold text-white bg-red-500 rounded-full ring-2 ring-white dark:ring-gray-800">
                  {unreadCount > 9 ? '9+' : unreadCount}
                </span>
              )}
            </button>

            {showNotifications && (
              <div className="absolute right-0 mt-2 w-80 bg-white dark:bg-gray-800 rounded-xl shadow-elevated ring-1 ring-gray-900/5 dark:ring-white/10 py-2 z-50 animate-slide-down">
                <div className="px-4 py-2 border-b border-gray-100 dark:border-gray-700 flex items-center justify-between">
                  <p className="font-semibold text-gray-900 dark:text-gray-100">{t('notifications.title')}</p>
                  {notifications.length > 0 && (
                    <span className="text-xs text-gray-500 dark:text-gray-400">{notifications.length}</span>
                  )}
                </div>
                <div className="max-h-96 overflow-y-auto">
                  {notifications.length === 0 ? (
                    <p className="px-4 py-8 text-sm text-center text-gray-500 dark:text-gray-400">
                      {t('notifications.empty')}
                    </p>
                  ) : (
                    notifications.map((n) => (
                      <button
                        key={n.id}
                        onClick={() => onNotifClick(n)}
                        className="w-full text-left px-4 py-3 flex gap-3 hover:bg-gray-50 dark:hover:bg-gray-700/50 border-b border-gray-100 dark:border-gray-700/60 last:border-0 transition-colors"
                      >
                        <span className="mt-0.5 shrink-0">{notifIcon(n.kind)}</span>
                        <span className="min-w-0 flex-1">
                          <span className="block text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                            {notifTitle(n)}
                          </span>
                          <span className="block text-xs text-gray-500 dark:text-gray-400 truncate">
                            {notifSubtitle(n)}
                          </span>
                        </span>
                      </button>
                    ))
                  )}
                </div>
              </div>
            )}
          </div>

          {/* User Avatar */}
          <div className="flex items-center gap-3 pl-2 sm:pl-3 ml-1 border-l border-gray-200 dark:border-gray-700">
            <div className="hidden sm:block text-right">
              <p className="text-sm font-medium text-gray-900 dark:text-gray-100 leading-tight">
                {user?.firstName && user?.lastName
                  ? `${user.firstName} ${user.lastName}`
                  : user?.username}
              </p>
              <p className="text-xs text-gray-500 dark:text-gray-400">{user?.role}</p>
            </div>
            <div className="w-9 h-9 bg-gradient-to-br from-primary-500 to-primary-700 rounded-full flex items-center justify-center shadow-soft ring-2 ring-white dark:ring-gray-800">
              <span className="text-white font-semibold text-sm">
                {user?.firstName?.charAt(0).toUpperCase() || user?.username?.charAt(0).toUpperCase()}
              </span>
            </div>
          </div>
        </div>
      </div>
    </header>
  );
};

export default Header;
