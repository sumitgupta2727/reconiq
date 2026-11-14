package com.reconiq.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.reconiq.dto.ReportSummaryResponse;
import com.reconiq.entity.Reconciliation;
import com.reconiq.entity.ReconciliationStatus;
import com.reconiq.exception.BadRequestException;
import com.reconiq.repository.OrderRepository;
import com.reconiq.repository.ReconciliationRepository;
import com.reconiq.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final ReconciliationRepository reconciliationRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    private final boolean emailEnabled;
    private final String defaultRecipient;
    private final String mailUsername;
    private final boolean emailConfigured;

    public ReportService(OrderRepository orderRepository,
                         TransactionRepository transactionRepository,
                         ReconciliationRepository reconciliationRepository,
                         ObjectProvider<JavaMailSender> mailSenderProvider,
                         @Value("${reconiq.report.email.enabled:false}") boolean emailEnabled,
                         @Value("${reconiq.report.email.to:}") String defaultRecipient,
                         @Value("${spring.mail.username:}") String mailUsername,
                         @Value("${spring.mail.password:}") String mailPassword) {
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.mailSenderProvider = mailSenderProvider;
        this.emailEnabled = emailEnabled;
        this.defaultRecipient = defaultRecipient;
        this.mailUsername = mailUsername == null ? "" : mailUsername.trim();
        this.emailConfigured = !this.mailUsername.isEmpty()
                && mailPassword != null && !mailPassword.isBlank();
    }

    @Transactional(readOnly = true)
    public ReportSummaryResponse summary() {
        Map<String, Long> statusCounts = emptyStatusCounts();
        Map<String, BigDecimal> statusDifferences = emptyStatusDifferences();

        for (Object[] row : reconciliationRepository.aggregateByStatus()) {
            ReconciliationStatus status = (ReconciliationStatus) row[0];
            long count = ((Number) row[1]).longValue();
            BigDecimal difference = toBigDecimal(row[2]);
            statusCounts.put(status.name(), count);
            statusDifferences.put(status.name(), difference);
        }

        long totalRecords = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        long matchedRecords = statusCounts.getOrDefault(ReconciliationStatus.MATCHED.name(), 0L);
        BigDecimal orderTotal = nullSafe(orderRepository.sumOrderAmount());
        BigDecimal transactionTotal = nullSafe(transactionRepository.sumTransactionAmount());

        List<ReportSummaryResponse.GatewaySummary> gateways = new ArrayList<>();
        for (Object[] row : reconciliationRepository.aggregateByGateway(ReconciliationStatus.MATCHED)) {
            String gateway = (String) row[0];
            long records = ((Number) row[1]).longValue();
            long matched = ((Number) row[2]).longValue();
            gateways.add(new ReportSummaryResponse.GatewaySummary(
                    gateway, records, matched, records - matched, toBigDecimal(row[3])));
        }

        return new ReportSummaryResponse(
                LocalDateTime.now(),
                reconciliationRepository.findLastReconciledAt(),
                orderRepository.count(),
                transactionRepository.count(),
                totalRecords,
                matchedRecords,
                totalRecords - matchedRecords,
                orderTotal,
                transactionTotal,
                orderTotal.subtract(transactionTotal),
                nullSafe(reconciliationRepository.sumAbsoluteDiscrepancy(ReconciliationStatus.MATCHED)),
                statusCounts,
                statusDifferences,
                gateways);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv() {
        List<Reconciliation> results = reconciliationRepository.findAllByOrderByIdAsc();
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("order_id,transaction_id,gateway,order_amount,transaction_amount,difference,status\n");
        for (Reconciliation result : results) {
            csv.append(csvValue(result.getOrderId())).append(',')
                    .append(csvValue(result.getTransactionId())).append(',')
                    .append(csvValue(result.getGateway())).append(',')
                    .append(amount(result.getOrderAmount())).append(',')
                    .append(amount(result.getTransactionAmount())).append(',')
                    .append(amount(result.getDifference())).append(',')
                    .append(result.getReconciliationStatus().name()).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] exportPdf() {
        ReportSummaryResponse summary = summary();
        List<Reconciliation> results = reconciliationRepository.findAllByOrderByIdAsc();
        List<Reconciliation> issues = results.stream()
                .filter(result -> result.getReconciliationStatus() != ReconciliationStatus.MATCHED)
                .sorted(Comparator.comparing((Reconciliation result) ->
                                result.getDifference() == null ? BigDecimal.ZERO : result.getDifference().abs())
                        .reversed())
                .limit(40)
                .toList();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("ReconIQ - Payment Reconciliation Report",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Font.NORMAL, new Color(31, 79, 216))));
            document.add(new Paragraph("Generated " + summary.generatedAt().format(TIMESTAMP),
                    FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, Color.GRAY)));
            document.add(new Paragraph(" "));

            PdfPTable stats = new PdfPTable(new float[]{1, 1, 1, 1, 1, 1});
            stats.setWidthPercentage(100);
            headerCell(stats, "Orders", 9);
            headerCell(stats, "Transactions", 9);
            headerCell(stats, "Results", 9);
            headerCell(stats, "Matched", 9);
            headerCell(stats, "Issues", 9);
            headerCell(stats, "Amount at risk", 9);
            valueCell(stats, String.valueOf(summary.totalOrders()));
            valueCell(stats, String.valueOf(summary.totalTransactions()));
            valueCell(stats, String.valueOf(summary.totalReconciliationRecords()));
            valueCell(stats, String.valueOf(summary.matchedRecords()));
            valueCell(stats, String.valueOf(summary.issueRecords()));
            valueCell(stats, "Rs. " + money(summary.amountAtRisk()));
            document.add(stats);
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Status breakdown",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
            PdfPTable statusTable = new PdfPTable(new float[]{2, 1, 1.4f});
            statusTable.setWidthPercentage(60);
            statusTable.setHorizontalAlignment(Element.ALIGN_LEFT);
            headerCell(statusTable, "Status", 9);
            headerCell(statusTable, "Records", 9);
            headerCell(statusTable, "Difference", 9);
            summary.statusCounts().forEach((status, count) -> {
                valueCell(statusTable, status);
                valueCell(statusTable, String.valueOf(count));
                valueCell(statusTable, "Rs. " + money(summary.statusDifferences()
                        .getOrDefault(status, BigDecimal.ZERO)));
            });
            document.add(statusTable);
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Largest discrepancies",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
            if (issues.isEmpty()) {
                document.add(new Paragraph("No discrepancies were found. Every record is matched.",
                        FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, new Color(22, 163, 74))));
            } else {
                PdfPTable table = new PdfPTable(new float[]{1.1f, 1.1f, 1f, 1.1f, 1.1f, 1.1f, 1.6f});
                table.setWidthPercentage(100);
                for (String heading : List.of("Order ID", "Transaction ID", "Gateway", "Order amount",
                        "Txn amount", "Difference", "Status")) {
                    headerCell(table, heading, 8.5f);
                }
                for (Reconciliation result : issues) {
                    valueCell(table, orDash(result.getOrderId()));
                    valueCell(table, orDash(result.getTransactionId()));
                    valueCell(table, orDash(result.getGateway()));
                    valueCell(table, money(result.getOrderAmount()));
                    valueCell(table, money(result.getTransactionAmount()));
                    valueCell(table, money(result.getDifference()));
                    valueCell(table, result.getReconciliationStatus().name());
                }
                document.add(table);
            }

            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            log.error("Could not generate the PDF report", ex);
            throw new BadRequestException("The PDF report could not be generated");
        }
    }

    /**
     * Sends the PDF report by email. Requires {@code reconiq.report.email.enabled=true}
     * plus the standard {@code spring.mail.*} settings.
     */
    public void emailReport(String to) {
        if (!emailEnabled) {
            throw new BadRequestException(
                    "Email delivery is disabled. Set REPORT_EMAIL_ENABLED=true to use it.");
        }
        if (!emailConfigured) {
            throw new BadRequestException(
                    "SMTP is not configured. Set MAIL_USER and MAIL_PASS (Gmail app password) "
                            + "and restart the backend.");
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new BadRequestException(
                    "SMTP is not configured. Set MAIL_USER and MAIL_PASS to send reports.");
        }

        String recipient = to == null || to.isBlank() ? defaultRecipient : to.trim();
        if (recipient == null || recipient.isBlank()) {
            throw new BadRequestException("No recipient email address was provided");
        }

        ReportSummaryResponse summary = summary();
        byte[] pdf = exportPdf();

        try {
            MimeMessageHelper message = new MimeMessageHelper(mailSender.createMimeMessage(), true, "UTF-8");
            message.setFrom(mailUsername);
            message.setTo(recipient);
            message.setSubject("ReconIQ reconciliation report - " + LocalDate.now());
            message.setText(htmlSummary(summary), true);
            message.addAttachment("reconiq-report-" + LocalDateTime.now().format(FILE_STAMP) + ".pdf",
                    new ByteArrayResource(pdf), "application/pdf");
            mailSender.send(message.getMimeMessage());
            log.info("Reconciliation report sent to {}", recipient);
        } catch (Exception ex) {
            log.error("Could not send the reconciliation report to {}", recipient, ex);
            throw new BadRequestException("The report could not be emailed. Check the SMTP configuration.");
        }
    }

    /** Called after a reconciliation run; failures never break the run itself. */
    public void emailReportAfterRunIfEnabled() {
        if (!emailEnabled) {
            return;
        }
        try {
            emailReport(defaultRecipient);
        } catch (RuntimeException ex) {
            log.warn("Automatic report email was skipped: {}", ex.getMessage());
        }
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public boolean isEmailConfigured() {
        return emailEnabled && emailConfigured;
    }

    public String getSenderAddress() {
        return mailUsername;
    }

    public String getDefaultRecipient() {
        return defaultRecipient;
    }

    private String htmlSummary(ReportSummaryResponse summary) {
        Map<String, Long> counts = summary.statusCounts();
        StringBuilder rows = new StringBuilder();
        counts.forEach((status, count) -> rows.append("<tr><td>").append(status)
                .append("</td><td align=\"right\">").append(count).append("</td></tr>"));

        return """
                <div style="font-family:Segoe UI,Arial,sans-serif;color:#1b2333">
                  <h2 style="color:#1f4fd8;margin-bottom:4px">ReconIQ reconciliation report</h2>
                  <p style="color:#6b7488;margin-top:0">Generated %s</p>
                  <table cellpadding="6" cellspacing="0" border="0" style="border-collapse:collapse">
                    <tr><td>Orders</td><td><b>%d</b></td></tr>
                    <tr><td>Gateway transactions</td><td><b>%d</b></td></tr>
                    <tr><td>Matched records</td><td><b>%d</b></td></tr>
                    <tr><td>Open issues</td><td><b>%d</b></td></tr>
                    <tr><td>Amount at risk</td><td><b>Rs. %s</b></td></tr>
                  </table>
                  <h3>Status breakdown</h3>
                  <table cellpadding="4" cellspacing="0" border="0" style="border-collapse:collapse">%s</table>
                  <p>The full report is attached as a PDF.</p>
                </div>
                """.formatted(
                summary.generatedAt().format(TIMESTAMP),
                summary.totalOrders(),
                summary.totalTransactions(),
                summary.matchedRecords(),
                summary.issueRecords(),
                money(summary.amountAtRisk()),
                rows);
    }

    private static Map<String, Long> emptyStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ReconciliationStatus status : ReconciliationStatus.values()) {
            counts.put(status.name(), 0L);
        }
        return counts;
    }

    private static Map<String, BigDecimal> emptyStatusDifferences() {
        Map<String, BigDecimal> differences = new LinkedHashMap<>();
        for (ReconciliationStatus status : ReconciliationStatus.values()) {
            differences.put(status.name(), BigDecimal.ZERO);
        }
        return differences;
    }

    private static void headerCell(PdfPTable table, String text, float size) {
        PdfPCell cell = new PdfPCell(new Phrase(text,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, Font.NORMAL, Color.WHITE)));
        cell.setBackgroundColor(new Color(31, 79, 216));
        cell.setPadding(6);
        table.addCell(cell);
    }

    private static void valueCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text,
                FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Font.NORMAL, new Color(27, 35, 51))));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private static String csvValue(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static String amount(BigDecimal value) {
        return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String money(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
