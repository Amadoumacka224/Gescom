import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { act, cleanup, render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import '../i18n';
import axiosInstance from '../services/axios';
import AuthContext from '../context/AuthContext';
import OrderWorkspace from '../components/OrderWorkspace';
import Caisse from './Caisse';
import Caisses from './Caisses';
import Categories from './Categories';
import Clients from './Clients';
import Dashboard from './Dashboard';
import Deliveries from './Deliveries';
import History from './History';
import Invoices from './Invoices';
import Orders from './Orders';
import Products from './Products';
import Profile from './Profile';
import Reports from './Reports';
import Settings from './Settings';
import Stock from './Stock';
import Users from './Users';
import PlatformActivity from './platform/PlatformActivity';
import PlatformCompanies from './platform/PlatformCompanies';
import PlatformNotifications from './platform/PlatformNotifications';
import PlatformPayments from './platform/PlatformPayments';
import PlatformPlans from './platform/PlatformPlans';
import PlatformSettings from './platform/PlatformSettings';
import PlatformSubscriptions from './platform/PlatformSubscriptions';
import PlatformSupport from './platform/PlatformSupport';
import PlatformUsers from './platform/PlatformUsers';

/*
 * Chaque écran est affiché avec une API simulée, et le test échoue sur toute erreur de rendu.
 *
 * Ni le build ni le lint ne voient une variable lue avant sa déclaration dans le corps d'un
 * composant (`const` en zone morte temporelle) : l'erreur n'apparaît qu'à l'affichage de la
 * page. Ce test l'attrape, ainsi que les ouvertures par lien (`?orderId=`, `location.state`).
 */

const ORDER = {
  id: 3,
  orderNumber: 'CMD00003',
  status: 'CONFIRMED',
  totalAmount: 100,
  finalAmount: 100,
  items: [],
  client: { id: 1, firstName: 'Anne', lastName: 'Martin', address: 'Rue 1', city: 'Liège' },
};

// Objet de synthèse (compteurs, options de filtre, tableaux de bord) : toute propriété absente
// vaut une liste vide, ce qui suffit à afficher l'écran sans connaître chaque champ.
const anyShape = (base = {}) => new Proxy(base, { get: (target, key) => (key in target || typeof key !== 'string' ? target[key] : []) });

const PAGE = { content: [], totalElements: 0, totalPages: 0 };

// Réponses par motif d'URL, dans la forme que renvoie l'API réelle.
const responseFor = (url) => {
  if (/\/orders\/deliverable$/.test(url) || /^\/?orders$/.test(url)) return [ORDER];
  if (/\/orders\/search$/.test(url)) return { content: [{ ...ORDER, id: 7, orderNumber: 'CMD00007' }], totalElements: 1, totalPages: 1 };
  if (/\/orders\/\d+$/.test(url)) return ORDER;
  if (/\/invoices\/order\/\d+$/.test(url)) return { id: 9, status: 'UNPAID', invoiceNumber: 'FAC00009' };
  if (/\/invoices\/\d+$/.test(url)) return { id: 9, status: 'UNPAID', invoiceNumber: 'FAC00009', order: ORDER, items: [] };
  if (/\/(search|movements|returns|activities)$/.test(url)) return PAGE;
  if (/^\/?platform\/(companies|users|subscriptions|payments|notifications|support|activity)$/.test(url)) return PAGE;
  if (/\/dashboard\/cashiers?$/.test(url)) return anyShape({ selectedDate: '2026-11-02' });
  if (/\/(summary|filter-options|filters|dashboard|overview|cashier|cashiers|settings|me|unread-count|open-count)$/.test(url)) return anyShape();
  // Listes complètes : catégories, clients, produits, utilisateurs, formules…
  return [];
};

const adapter = (config) => Promise.resolve({
  data: responseFor(config.url.replace(/\?.*$/, '')),
  status: 200,
  statusText: 'OK',
  headers: {},
  config,
});

const ADMIN = { id: 1, username: 'admin', role: 'ADMIN', firstName: 'Ada', lastName: 'Min' };

const renderPage = async (element, { path = '/page', entry = '/page' } = {}) => {
  render(
    <AuthContext.Provider value={{ user: ADMIN, isAuthenticated: true, loading: false, updateUser: () => {} }}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path={path} element={element} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
  // Laisse les chargements se terminer et les rendus qui en découlent se faire.
  for (let i = 0; i < 5; i += 1) {
    await act(() => new Promise((resolve) => { setTimeout(resolve, 0); }));
  }
};

describe('écrans', () => {
  const defaultAdapter = axiosInstance.defaults.adapter;
  let errors;

  beforeAll(() => {
    axiosInstance.defaults.adapter = adapter;
    // jsdom n'a pas ces API, utilisées par les graphiques et le défilement.
    globalThis.ResizeObserver ??= class { observe() {} unobserve() {} disconnect() {} };
    Element.prototype.scrollIntoView ??= () => {};
    window.scrollTo = () => {};
  });

  afterAll(() => {
    axiosInstance.defaults.adapter = defaultAdapter;
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    localStorage.clear();
  });

  const expectNoRenderError = () => {
    const messages = errors.mock.calls.map((args) => args.map(String).join(' '));
    expect(messages.filter((m) => /Error|before initialization|is not defined|is not a function/.test(m))).toEqual([]);
  };

  const pages = {
    Caisse, Caisses, Categories, Clients, Dashboard, Deliveries, History, Invoices, Orders,
    Products, Profile, Reports, Settings, Stock, Users,
    PlatformActivity, PlatformCompanies, PlatformNotifications, PlatformPayments, PlatformPlans,
    PlatformSettings, PlatformSubscriptions, PlatformSupport, PlatformUsers,
  };

  it.each(Object.entries(pages))('%s s’affiche sans erreur', async (_, Page) => {
    errors = vi.spyOn(console, 'error');
    await renderPage(<Page />);
    expectNoRenderError();
  });

  it('Commandes : ?orderId= ouvre le détail de la commande', async () => {
    errors = vi.spyOn(console, 'error');
    await renderPage(<Orders />, { entry: '/page?orderId=7' });
    expectNoRenderError();
    expect(document.body.textContent).toContain('CMD00007');
  });

  it('Factures : createForOrderId ouvre le formulaire avec la commande choisie', async () => {
    errors = vi.spyOn(console, 'error');
    await renderPage(<Invoices />, { entry: { pathname: '/page', state: { createForOrderId: 3 } } });
    expectNoRenderError();
    expect(document.body.textContent).toContain('CMD00003');
  });

  it('Factures : invoiceId ouvre le détail de la facture', async () => {
    errors = vi.spyOn(console, 'error');
    await renderPage(<Invoices />, { entry: { pathname: '/page', state: { invoiceId: 9 } } });
    expectNoRenderError();
    expect(document.body.textContent).toContain('FAC00009');
  });

  it('Panier : une commande facturée s’ouvre et charge sa facture', async () => {
    errors = vi.spyOn(console, 'error');
    const invoiced = { ...ORDER, status: 'INVOICED' };
    const props = { onClose: () => {}, products: [], clients: [], categories: [], onDataChanged: () => {} };
    const { rerender } = render(<MemoryRouter><OrderWorkspace isOpen={false} {...props} /></MemoryRouter>);
    rerender(<MemoryRouter><OrderWorkspace isOpen initialOrder={invoiced} {...props} /></MemoryRouter>);
    for (let i = 0; i < 5; i += 1) {
      await act(() => new Promise((resolve) => { setTimeout(resolve, 0); }));
    }
    expectNoRenderError();
    expect(document.body.textContent).toContain('CMD00003');
  });

  it('Livraisons : createForOrderId ouvre le formulaire avec l’adresse du client', async () => {
    errors = vi.spyOn(console, 'error');
    await renderPage(<Deliveries />, { entry: { pathname: '/page', state: { createForOrderId: 3 } } });
    expectNoRenderError();
    expect(document.querySelector('input[value="Liège"]')).not.toBeNull();
  });
});
