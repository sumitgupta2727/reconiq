import api from "./api.js";

const uploadConfig = {
  headers: { "Content-Type": "multipart/form-data" },
  timeout: 120000,
};

function buildFormData(file) {
  const formData = new FormData();
  formData.append("file", file);
  return formData;
}

function progressHandler(onProgress) {
  return (event) => {
    if (!onProgress || !event.total) return;
    onProgress(Math.round((event.loaded * 100) / event.total));
  };
}

const uploadService = {
  async upload(file, uploadedBy, onProgress) {
    const { data } = await api.post("/uploads", buildFormData(file), {
      ...uploadConfig,
      params: uploadedBy ? { uploadedBy } : undefined,
      onUploadProgress: progressHandler(onProgress),
    });
    return data;
  },

  async uploadOrders(file, uploadedBy, onProgress) {
    const { data } = await api.post("/uploads/orders", buildFormData(file), {
      ...uploadConfig,
      params: uploadedBy ? { uploadedBy } : undefined,
      onUploadProgress: progressHandler(onProgress),
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

  async downloadTemplate(type = "gateway") {
    const path = type === "orders" ? "/uploads/orders/template" : "/uploads/template";
    const response = await api.get(path, { responseType: "blob" });
    return response.data;
  },

  async downloadOrdersTemplate() {
    return uploadService.downloadTemplate("orders");
  },
};

export default uploadService;
