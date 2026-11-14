package com.reconiq.dto;

import com.reconiq.entity.UploadBatch;

import java.time.LocalDateTime;

public record UploadBatchResponse(
        Long id,
        String fileName,
        String fileType,
        String dataType,
        Integer totalRecords,
        long importedRecords,
        String status,
        String errorMessage,
        String uploadedBy,
        LocalDateTime uploadedAt) {

    public static UploadBatchResponse from(UploadBatch batch) {
        return new UploadBatchResponse(
                batch.getId(),
                batch.getFileName(),
                batch.getFileType(),
                batch.getDataType() == null ? UploadBatch.DataType.GATEWAY.name() : batch.getDataType().name(),
                batch.getTotalRecords(),
                batch.getImportedRecords() == null ? 0 : batch.getImportedRecords(),
                batch.getStatus().name(),
                batch.getErrorMessage(),
                batch.getUploadedBy() == null ? null : batch.getUploadedBy().getName(),
                batch.getUploadedAt());
    }
}
