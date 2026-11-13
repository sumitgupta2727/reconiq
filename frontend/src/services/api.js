import axios from "axios";

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";

/**
 * Shared Axios instance used by every service.
 * Timeouts are deliberately short so a stopped backend fails fast with a
 * readable message instead of hanging the UI.
 */
const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 20000,
  headers: { Accept: "application/json" },
});

export default api;
