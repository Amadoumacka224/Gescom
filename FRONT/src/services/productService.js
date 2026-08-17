import axios from './axios';

const productService = {
  /**
   * Catalogue intégral. Réservé aux écrans qui s'en servent de référentiel — la caisse,
   * l'ajustement de stock, le décompte par catégorie. Le tableau de l'écran Produits passe
   * par searchProducts : lui renvoyer tout le catalogue pour n'en afficher qu'une page était
   * précisément le défaut corrigé.
   */
  getAllProducts: () => axios.get('/products'),

  /**
   * Page du catalogue, filtrée et triée par le serveur.
   *
   * `params` : { page, size, sort, search, categoryId, active }. `page` est l'index de Spring
   * Data, donc à partir de 0 — l'interface compte à partir de 1, la conversion est faite par
   * l'appelant. `sort` suit la forme « champ,sens » (ex. « name,desc »).
   */
  searchProducts: (params) => axios.get('/products/search', { params }),

  /** Compteurs d'en-tête : ils portent sur le catalogue entier, pas sur la page affichée. */
  getCatalogSummary: () => axios.get('/products/summary'),

  // Export CSV (réservé à l'ADMIN côté backend) : réponse binaire, d'où `responseType: 'blob'`.
  exportProducts: () => axios.get('/products/export', { responseType: 'blob' }),
  getActiveProducts: () => axios.get('/products/active'),
  getProductById: (id) => axios.get(`/products/${id}`),
  getProductByBarcode: (barcode) => axios.get(`/products/barcode/${encodeURIComponent(barcode)}`),
  getLowStockProducts: () => axios.get('/products/low-stock'),
  getCategories: () => axios.get('/categories').then(res => res.data),
  createProduct: (product) => axios.post('/products', product),
  updateProduct: (id, product) => axios.put(`/products/${id}`, product),
  updateStock: (id, quantity) => axios.patch(`/products/${id}/stock`, { quantity }),
  deleteProduct: (id) => axios.delete(`/products/${id}`),
};

export default productService;
