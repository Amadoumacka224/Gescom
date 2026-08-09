import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider } from './context/AuthContext';
import { useAuth } from './context/useAuth';
import ProtectedRoute from './components/ProtectedRoute';
import MainLayout from './layouts/MainLayout';
import PlatformLayout from './layouts/PlatformLayout';
import PlatformDashboard from './pages/platform/PlatformDashboard';
import PlatformCompanies from './pages/platform/PlatformCompanies';
import PlatformSubscriptions from './pages/platform/PlatformSubscriptions';
import PlatformPayments from './pages/platform/PlatformPayments';
import PlatformActivity from './pages/platform/PlatformActivity';
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
import Reports from './pages/Reports';
import Caisse from './pages/Caisse';
import Caisses from './pages/Caisses';
import History from './pages/History';
import './i18n';

// Redirige vers l'accueil adapté au rôle. Le propriétaire de la plateforme n'a pas d'accueil
// métier : il n'appartient à aucune entreprise et atterrit sur son back-office.
const HomeRedirect = () => {
  const { user } = useAuth();
  if (user?.role === 'SUPER_ADMIN') return <Navigate to="/platform" replace />;
  return <Navigate to={user?.role === 'CAISSIER' ? '/caisse' : '/dashboard'} replace />;
};

// Garde les pages réservées à l'administrateur : tout autre rôle est renvoyé vers sa caisse.
const AdminRoute = ({ children }) => {
  const { user } = useAuth();
  return user?.role === 'ADMIN' ? children : <Navigate to="/caisse" replace />;
};

/* Garde du back-office propriétaire.
 *
 * Le contrôle décisif reste côté serveur : /api/platform/** exige le rôle SUPER_ADMIN, et
 * aucune donnée du parc ne transite sans lui. Cette garde-ci ne fait qu'éviter d'afficher
 * une coquille vide à qui n'y a pas droit. */
const PlatformRoute = ({ children }) => {
  const { user } = useAuth();
  return user?.role === 'SUPER_ADMIN' ? children : <Navigate to="/" replace />;
};

function App() {
  return (
    <AuthProvider>
      <Toaster position="top-right" />
      <Router>
        <Routes>
          {/* Public Route */}
          <Route path="/login" element={<Login />} />

          {/* Back-office du propriétaire de la plateforme.
              Espace à part entière : sa propre coquille, sa propre navigation, aucun écran
              métier. Déclaré avant /* pour que ces chemins ne tombent pas dans MainLayout. */}
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
            <Route path="subscriptions" element={<PlatformSubscriptions />} />
            <Route path="payments" element={<PlatformPayments />} />
            <Route path="activity" element={<PlatformActivity />} />
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
      </Router>
    </AuthProvider>
  );
}

export default App;
