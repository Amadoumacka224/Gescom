import { lazy, Suspense } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider } from './context/AuthContext';
import { useAuth } from './context/useAuth';
import ProtectedRoute from './components/ProtectedRoute';
import MainLayout from './layouts/MainLayout';
import PlatformLayout from './layouts/PlatformLayout';
import PlatformDashboard from './pages/platform/PlatformDashboard';
import PlatformCompanies from './pages/platform/PlatformCompanies';
import PlatformUsers from './pages/platform/PlatformUsers';
import PlatformPlans from './pages/platform/PlatformPlans';
import PlatformSubscriptions from './pages/platform/PlatformSubscriptions';
import PlatformPayments from './pages/platform/PlatformPayments';
import PlatformActivity from './pages/platform/PlatformActivity';
import PlatformSettings from './pages/platform/PlatformSettings';
import PlatformSupport from './pages/platform/PlatformSupport';
import PlatformNotifications from './pages/platform/PlatformNotifications';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Clients from './pages/Clients';
import Products from './pages/Products';
import Categories from './pages/Categories';
import Orders from './pages/Orders';
import Deliveries from './pages/Deliveries';
import Invoices from './pages/Invoices';
import Stock from './pages/Stock';
import Users from './pages/Users';
import Settings from './pages/Settings';
import Profile from './pages/Profile';
import History from './pages/History';
import './i18n';

/*
 * Les trois écrans avec graphiques sont chargés à la demande.
 *
 * Ce sont les seuls qui utilisent recharts (340 ko avec d3). En import classique, toutes les
 * pages payaient ce poids, même la page de connexion. Ils sont regroupés dans le paquet
 * `charts` (voir vite.config.js) : la librairie n'est téléchargée qu'une fois, au premier
 * des trois écrans ouverts.
 *
 * Pas la peine de découper les autres pages, elles n'ont pas de grosse dépendance : on
 * ajouterait juste un temps d'attente à chaque navigation.
 */
const Reports = lazy(() => import('./pages/Reports'));
const Caisse = lazy(() => import('./pages/Caisse'));
const Caisses = lazy(() => import('./pages/Caisses'));

// Redirige vers la bonne page d'accueil selon le rôle. Le super admin n'a pas d'entreprise,
// donc il arrive directement sur son back-office.
const HomeRedirect = () => {
  const { user } = useAuth();
  if (user?.role === 'SUPER_ADMIN') return <Navigate to="/platform" replace />;
  return <Navigate to={user?.role === 'CAISSIER' ? '/caisse' : '/dashboard'} replace />;
};

// Pages réservées à l'admin : les autres rôles sont renvoyés vers leur caisse.
const AdminRoute = ({ children }) => {
  const { user } = useAuth();
  return user?.role === 'ADMIN' ? children : <Navigate to="/caisse" replace />;
};

/* Protection du back-office plateforme.
 *
 * La vraie vérification se fait côté serveur (/api/platform/** demande SUPER_ADMIN).
 * Ici on évite juste d'afficher une page vide à quelqu'un qui n'a pas le droit. */
const PlatformRoute = ({ children }) => {
  const { user } = useAuth();
  return user?.role === 'SUPER_ADMIN' ? children : <Navigate to="/" replace />;
};

/* Affiché pendant le chargement d'un écran. On met un squelette plutôt qu'un spinner pour
 * que la mise en page ne saute pas quand le contenu arrive. */
const RouteFallback = () => (
  <div className="space-y-4 p-6" aria-busy="true">
    <div className="h-24 rounded-xl bg-gray-100 dark:bg-gray-700/40 animate-pulse" />
    <div className="h-64 rounded-xl bg-gray-100 dark:bg-gray-700/40 animate-pulse" />
  </div>
);

function App() {
  return (
    <AuthProvider>
      <Toaster position="top-right" />
      <Router>
        {/* Un seul Suspense autour de <Routes> : le même squelette sert pour les trois
            écrans chargés à la demande. */}
        <Suspense fallback={<RouteFallback />}>
          <Routes>
            {/* Public Route */}
            <Route path="/login" element={<Login />} />

            {/* Back-office de la plateforme : layout et menu à part, aucun écran métier.
                Déclaré avant /* pour que ces routes ne passent pas par MainLayout. */}
            <Route
              path="/platform"
              element={
                <ProtectedRoute>
                  <PlatformRoute>
                    <PlatformLayout />
                  </PlatformRoute>
                </ProtectedRoute>
              }
            >
              <Route index element={<PlatformDashboard />} />
              <Route path="companies" element={<PlatformCompanies />} />
              <Route path="users" element={<PlatformUsers />} />
              <Route path="subscriptions" element={<PlatformSubscriptions />} />
              <Route path="plans" element={<PlatformPlans />} />
              <Route path="payments" element={<PlatformPayments />} />
              <Route path="support" element={<PlatformSupport />} />
              <Route path="notifications" element={<PlatformNotifications />} />
              <Route path="activity" element={<PlatformActivity />} />
              <Route path="settings" element={<PlatformSettings />} />
            </Route>

            {/* Protected Routes */}
            <Route
              path="/*"
              element={
                <ProtectedRoute>
                  <MainLayout />
                </ProtectedRoute>
              }
            >
              <Route path="dashboard" element={<AdminRoute><Dashboard /></AdminRoute>} />
              <Route path="clients" element={<Clients />} />
              <Route path="products" element={<Products />} />
              <Route path="categories" element={<Categories />} />
              <Route path="orders" element={<Orders />} />
              <Route path="deliveries" element={<Deliveries />} />
              <Route path="invoices" element={<Invoices />} />
              <Route path="stock" element={<AdminRoute><Stock /></AdminRoute>} />
              <Route path="users" element={<AdminRoute><Users /></AdminRoute>} />
              <Route path="settings" element={<AdminRoute><Settings /></AdminRoute>} />
              <Route path="profile" element={<Profile />} />
              <Route path="reports" element={<AdminRoute><Reports /></AdminRoute>} />
              <Route path="caisse" element={<Caisse />} />
              <Route path="caisses" element={<AdminRoute><Caisses /></AdminRoute>} />
              <Route path="history" element={<AdminRoute><History /></AdminRoute>} />
              <Route index element={<HomeRedirect />} />
            </Route>

            {/* Redirection de la racine selon le rôle */}
            <Route path="/" element={<HomeRedirect />} />
          </Routes>
        </Suspense>
      </Router>
    </AuthProvider>
  );
}

export default App;
