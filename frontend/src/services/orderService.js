import api from "./api.js";

const orderService = {
  async list(params) {
    const { data } = await api.get("/orders", { params });
    return data;
  },

  async get(id) {
    const { data } = await api.get(`/orders/${id}`);
    return data;
  },

  async gateways() {
    const { data } = await api.get("/orders/gateways");
    return data;
  },

  async create(payload) {
    const { data } = await api.post("/orders", payload);
    return data;
  },

  async update(id, payload) {
    const { data } = await api.put(`/orders/${id}`, payload);
    return data;
  },

  async remove(id) {
    await api.delete(`/orders/${id}`);
  },
};

export default orderService;
