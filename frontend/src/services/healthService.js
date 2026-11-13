import api from "./api.js";

const healthService = {
  async check() {
    const { data } = await api.get("/health", { timeout: 8000 });
    return data;
  },
};

export default healthService;
