import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider, useAuth } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import MainLayout from './layouts/MainLayout';
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

// Redirige vers l'accueil adapté au rôle : le caissier va sur sa caisse, l'admin sur le dashboard.
const HomeRedirect = () => {
  const { user } = useAuth();
  return <Navigate to={user?.role === 'CAISSIER' ? '/caisse' : '/dashboard'} replace />;
};

// Garde les pages réservées à l'administrateur : tout autre rôle est renvoyé vers sa caisse.
const AdminRoute = ({ children }) => {
  const { user } = useAuth();
  return user?.role === 'ADMIN' ? children : <Navigate to="/caisse" replace />;
};

function App() {
  return (
    <AuthProvider>
      <Toaster position="top-right" />
      <Router>
        <Routes>
          {/* Public Route */}
          <Route path="/login" element={<Login />} />

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
