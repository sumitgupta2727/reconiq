import api from "./api.js";
import { fileNameFromHeaders } from "../utils/download.js";

const reportService = {
  async summary() {
    const { data } = await api.get("/reports/summary");
    return data;
  },

  async exportCsv() {
    const response = await api.get("/reports/export/csv", { responseType: "blob" });
    return {
      blob: response.data,
      fileName: fileNameFromHeaders(response.headers, "reconiq-report.csv"),
    };
  },

  async exportPdf() {
    const response = await api.get("/reports/export/pdf", {
      responseType: "blob",
      timeout: 60000,
    });
    return {
      blob: response.data,
      fileName: fileNameFromHeaders(response.headers, "reconiq-report.pdf"),
    };
  },

  async emailConfig() {
    const { data } = await api.get("/reports/email/config");
    return data;
  },

  async emailReport(to) {
    const { data } = await api.post("/reports/email", to ? { to } : {}, { timeout: 60000 });
    return data;
  },
};

export default reportService;
