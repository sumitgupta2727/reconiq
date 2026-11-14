package com.reconiq.controller;

import com.reconiq.dto.EmailReportRequest;
import com.reconiq.dto.ReportSummaryResponse;
import com.reconiq.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    public ReportSummaryResponse summary() {
        return reportService.summary();
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv() {
        return download(reportService.exportCsv(), "csv", new MediaType("text", "csv"));
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf() {
        return download(reportService.exportPdf(), "pdf", MediaType.APPLICATION_PDF);
    }

    @PostMapping("/email")
    public Map<String, Object> email(@Valid @RequestBody(required = false) EmailReportRequest request) {
        String to = request == null ? null : request.to();
        reportService.emailReport(to);
        return Map.of(
                "sent", true,
                "to", to == null || to.isBlank() ? reportService.getDefaultRecipient() : to,
                "sentAt", LocalDateTime.now().toString());
    }

    @GetMapping("/email/config")
    public Map<String, Object> emailConfig() {
        return Map.of(
                "enabled", reportService.isEmailEnabled(),
                "configured", reportService.isEmailConfigured(),
                "from", reportService.getSenderAddress() == null ? "" : reportService.getSenderAddress(),
                "defaultRecipient", reportService.getDefaultRecipient() == null
                        ? "" : reportService.getDefaultRecipient());
    }

    private ResponseEntity<byte[]> download(byte[] body, String extension, MediaType mediaType) {
        String fileName = "reconiq-report-" + LocalDateTime.now().format(FILE_STAMP) + "." + extension;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(mediaType)
                .contentLength(body.length)
                .body(body);
    }
}
