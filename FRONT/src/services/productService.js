import axios from './axios';

const productService = {
  getAllProducts: () => axios.get('/products'),
  getActiveProducts: () => axios.get('/products/active'),
  getProductById: (id) => axios.get(`/products/${id}`),
  getLowStockProducts: () => axios.get('/products/low-stock'),
  getCategories: () => axios.get('/categories').then(res => res.data),
  createProduct: (product) => axios.post('/products', product),
  updateProduct: (id, product) => axios.put(`/products/${id}`, product),
  updateStock: (id, quantity) => axios.patch(`/products/${id}/stock`, { quantity }),
  deleteProduct: (id) => axios.delete(`/products/${id}`),
};

export default productService;
