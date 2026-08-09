import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Menu } from 'lucide-react';
import PlatformSidebar from '../components/PlatformSidebar';

/**
 * Coquille du back-office propriétaire.
 *
 * Elle ne réutilise pas `Header`, qui porte des éléments propres à l'espace métier (recherche
 * catalogue, alertes de stock, sélection d'entreprise). Le propriétaire de la plateforme n'a
 * rien à y faire : son en-tête se limite au titre de l'espace et à l'ouverture du menu mobile.
 */
const PlatformLayout = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const { t } = useTranslation();

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <PlatformSidebar isOpen={sidebarOpen} setIsOpen={setSidebarOpen} />

      <div className="flex-1 flex flex-col min-w-0 lg:pl-64">
        <header className="sticky top-0 z-20 flex h-16 shrink-0 items-center gap-3 border-b border-gray-200 bg-white/90 px-4 backdrop-blur sm:px-6 dark:border-gray-700 dark:bg-gray-800/90">
          <button
            onClick={() => setSidebarOpen(!sidebarOpen)}
            aria-label={t('nav.openMenu')}
            className="rounded-lg p-2 text-gray-500 hover:bg-gray-100 lg:hidden dark:text-gray-400 dark:hover:bg-gray-700"
          >
            <Menu className="h-5 w-5" />
          </button>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
              {t('platform.brand')}
            </p>
            <p className="truncate text-[11px] text-gray-500 dark:text-gray-400">
              {t('platform.tagline')}
            </p>
          </div>
        </header>

        <main className="flex-1 p-4 sm:p-6 overflow-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default PlatformLayout;
