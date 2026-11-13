package com.reconiq.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Standard error payload returned by the API. */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors,
        List<String> details) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(LocalDateTime.now(), status, error, message, path, null, null);
    }
}
