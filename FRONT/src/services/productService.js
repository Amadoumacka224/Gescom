import axios from './axios';

const productService = {
  /**
   * Tout le catalogue. Seulement pour les écrans qui ont besoin de la liste complète (la
   * caisse, l'ajustement de stock, le nombre de produits par catégorie). Le tableau de
   * l'écran Produits utilise searchProducts : charger tout le catalogue pour n'en afficher
   * qu'une page, c'était justement le problème qu'on a corrigé.
   */
  getAllProducts: () => axios.get('/products'),

  /**
   * Une page du catalogue, filtrée et triée par le serveur.
   *
   * `params` : { page, size, sort, search, categoryId, active }. `page` suit Spring Data,
   * donc commence à 0 (l'interface commence à 1, c'est l'appelant qui convertit). `sort` est
   * de la forme « champ,sens » (ex. « name,desc »).
   */
  searchProducts: (params) => axios.get('/products/search', { params }),

  /** Compteurs en haut de page : sur tout le catalogue, pas sur la page affichée. */
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
