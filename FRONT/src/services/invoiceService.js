import axios from './axios';

const invoiceService = {
  getAllInvoices: () => axios.get('/invoices'),
  getInvoiceById: (id) => axios.get(`/invoices/${id}`),
  getInvoiceByOrder: (orderId) => axios.get(`/invoices/order/${orderId}`),
  getOverdueInvoices: () => axios.get('/invoices/overdue'),
  createInvoice: (invoice) => axios.post('/invoices', invoice),
  recordPayment: (id, amount, paymentMethod) =>
    axios.patch(`/invoices/${id}/payment`, { amount, paymentMethod }),
  cancelInvoice: (id) => axios.patch(`/invoices/${id}/cancel`),
  deleteInvoice: (id) => axios.delete(`/invoices/${id}`),
};

export default invoiceService;
