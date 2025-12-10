import axios from './axios';

const deliveryService = {
  getAllDeliveries: () => axios.get('/deliveries'),
  getDeliveryById: (id) => axios.get(`/deliveries/${id}`),
  getDeliveryByOrder: (orderId) => axios.get(`/deliveries/order/${orderId}`),
  createDelivery: (delivery) => axios.post('/deliveries', delivery),
  updateDeliveryStatus: (id, status) => axios.patch(`/deliveries/${id}/status`, { status }),
  markAsDelivered: (id, deliveredBy) => axios.patch(`/deliveries/${id}/mark-delivered`, { deliveredBy }),
  deleteDelivery: (id) => axios.delete(`/deliveries/${id}`),
};

export default deliveryService;
