package com.reconiq.dto;

import com.reconiq.entity.UploadBatch;

import java.time.LocalDateTime;

public record UploadBatchResponse(
        Long id,
        String fileName,
        String fileType,
        Integer totalRecords,
        long importedRecords,
        String status,
        String errorMessage,
        String uploadedBy,
        LocalDateTime uploadedAt) {

    public static UploadBatchResponse from(UploadBatch batch, long importedRecords) {
        return new UploadBatchResponse(
                batch.getId(),
                batch.getFileName(),
                batch.getFileType(),
                batch.getTotalRecords(),
                importedRecords,
                batch.getStatus().name(),
                batch.getErrorMessage(),
                batch.getUploadedBy() == null ? null : batch.getUploadedBy().getName(),
                batch.getUploadedAt());
    }
}
