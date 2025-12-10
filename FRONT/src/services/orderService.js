import axios from './axios';

const orderService = {
  getAllOrders: () => axios.get('/orders'),
  getOrderById: (id) => axios.get(`/orders/${id}`),
  getOrdersByClient: (clientId) => axios.get(`/orders/client/${clientId}`),
  getOrdersByStatus: (status) => axios.get(`/orders/status/${status}`),
  createOrder: (order) => axios.post('/orders', order),
  updateOrderStatus: (id, status) => axios.patch(`/orders/${id}/status`, { status }),
  cancelOrder: (id) => axios.patch(`/orders/${id}/cancel`),
  deleteOrder: (id) => axios.delete(`/orders/${id}`),
};

export default orderService;
