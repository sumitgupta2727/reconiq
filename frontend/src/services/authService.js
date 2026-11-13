import api from "./api.js";

const authService = {
  async login(credentials) {
    const { data } = await api.post("/auth/login", credentials);
    return data;
  },
};

export default authService;
