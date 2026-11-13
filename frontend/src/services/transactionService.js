import api from "./api.js";

const transactionService = {
  async list(params) {
    const { data } = await api.get("/transactions", { params });
    return data;
  },

  async get(id) {
    const { data } = await api.get(`/transactions/${id}`);
    return data;
  },

  async gateways() {
    const { data } = await api.get("/transactions/gateways");
    return data;
  },

  async create(payload) {
    const { data } = await api.post("/transactions", payload);
    return data;
  },

  async update(id, payload) {
    const { data } = await api.put(`/transactions/${id}`, payload);
    return data;
  },

  async remove(id) {
    await api.delete(`/transactions/${id}`);
  },
};

export default transactionService;
