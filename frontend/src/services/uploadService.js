import api from "./api.js";

const uploadService = {
  async upload(file, uploadedBy, onProgress) {
    const formData = new FormData();
    formData.append("file", file);

    const { data } = await api.post("/uploads", formData, {
      params: uploadedBy ? { uploadedBy } : undefined,
      headers: { "Content-Type": "multipart/form-data" },
      timeout: 120000,
      onUploadProgress: (event) => {
        if (!onProgress || !event.total) return;
        onProgress(Math.round((event.loaded * 100) / event.total));
      },
    });
    return data;
  },

  async history() {
    const { data } = await api.get("/uploads");
    return data;
  },

  async get(id) {
    const { data } = await api.get(`/uploads/${id}`);
    return data;
  },

  async downloadTemplate() {
    const response = await api.get("/uploads/template", { responseType: "blob" });
    return response.data;
  },
};

export default uploadService;
