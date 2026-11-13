import api from "./api.js";

const reconciliationService = {
  async run() {
    const { data } = await api.post("/reconciliation/run", null, { timeout: 120000 });
    return data;
  },

  async list(params) {
    const { data } = await api.get("/reconciliation", { params });
    return data;
  },

  async get(id) {
    const { data } = await api.get(`/reconciliation/${id}`);
    return data;
  },

  async gateways() {
    const { data } = await api.get("/reconciliation/gateways");
    return data;
  },
};

export default reconciliationService;
