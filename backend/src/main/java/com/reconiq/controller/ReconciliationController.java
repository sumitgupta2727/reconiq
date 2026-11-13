package com.reconiq.controller;

import com.reconiq.dto.PageResponse;
import com.reconiq.dto.ReconciliationResponse;
import com.reconiq.dto.ReconciliationSummary;
import com.reconiq.entity.ReconciliationStatus;
import com.reconiq.service.ReconciliationService;
import com.reconiq.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final ReportService reportService;

    public ReconciliationController(ReconciliationService reconciliationService, ReportService reportService) {
        this.reconciliationService = reconciliationService;
        this.reportService = reportService;
    }

    /** Re-runs the engine, replacing all previous results. */
    @PostMapping("/run")
    public ResponseEntity<ReconciliationSummary> run() {
        ReconciliationSummary summary = reconciliationService.run();
        reportService.emailReportAfterRunIfEnabled();
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    @GetMapping
    public PageResponse<ReconciliationResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String gateway,
            @RequestParam(required = false) ReconciliationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reconciliationService.search(page, size, sortBy, direction, search, gateway, status, from, to);
    }

    @GetMapping("/gateways")
    public List<String> gateways() {
        return reconciliationService.gateways();
    }

    @GetMapping("/{id}")
    public ReconciliationResponse get(@PathVariable Long id) {
        return reconciliationService.get(id);
    }
}
