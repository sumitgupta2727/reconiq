package com.reconiq.controller;

import com.reconiq.dto.UploadBatchResponse;
import com.reconiq.service.UploadService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadBatchResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String uploadedBy) {
        UploadBatchResponse response = uploadService.upload(file, uploadedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<UploadBatchResponse> history() {
        return uploadService.history();
    }

    @GetMapping("/{id}")
    public UploadBatchResponse get(@PathVariable Long id) {
        return uploadService.get(id);
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] body = uploadService.csvTemplate().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reconiq-upload-template.csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(body);
    }
}
