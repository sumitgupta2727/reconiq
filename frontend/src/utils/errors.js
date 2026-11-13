/** Extracts the most useful message from an Axios error. */
export function getErrorMessage(error, fallback = "Something went wrong. Please try again.") {
  if (!error) return fallback;
  const data = error.response?.data;

  if (data?.fieldErrors) {
    const messages = Object.entries(data.fieldErrors).map(
      ([field, message]) => `${field}: ${message}`
    );
    if (messages.length) return messages.join(", ");
  }

  if (data?.details?.length) return data.details.join(", ");
  if (typeof data === "string" && data.trim()) return data;
  if (data?.message) return data.message;

  if (error.code === "ECONNABORTED") {
    return "The request timed out. Check that the backend is running.";
  }
  if (!error.response) {
    return "Cannot reach the ReconIQ API. Check that the backend is running on port 8080.";
  }
  return error.message || fallback;
}
