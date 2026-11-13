package com.reconiq.service;

import com.reconiq.entity.PaymentStatus;
import com.reconiq.entity.Transaction;
import com.reconiq.exception.BadRequestException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses payment-gateway CSV statements without touching the database.
 *
 * <p>Header names are normalised, so both {@code ORDER ID} and
 * {@code order_id} are accepted. Invalid rows are reported individually and
 * skipped, which lets the caller import the valid part of a file.</p>
 */
@Component
public class GatewayCsvParser {

    public static final int MAX_ROWS = 50_000;

    private static final Map<String, Set<String>> HEADER_ALIASES = Map.of(
            "order_id", Set.of("order_id", "orderid", "order_no", "order_number", "order"),
            "transaction_id", Set.of("transaction_id", "transactionid", "txn_id", "txnid",
                    "transaction_ref", "payment_id", "paymentid", "reference"),
            "customer_name", Set.of("customer_name", "customername", "customer", "name"),
            "gateway", Set.of("gateway", "payment_gateway", "provider", "gateway_name"),
            "transaction_amount", Set.of("transaction_amount", "transactionamount", "amount",
                    "paid_amount", "payment_amount", "value"),
            "transaction_date", Set.of("transaction_date", "transactiondate", "date",
                    "payment_date", "created_at", "timestamp"),
            "payment_status", Set.of("payment_status", "paymentstatus", "status", "payment_state")
    );

    private static final Set<String> REQUIRED_HEADERS =
            Set.of("transaction_id", "gateway", "transaction_amount", "transaction_date", "payment_status");

    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss")
    );

    private static final List<DateTimeFormatter> DATE_ONLY_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    );

    public record Result(List<Transaction> transactions, List<String> errors, int totalRows) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    public Result parse(InputStream inputStream) throws IOException {
        List<Transaction> transactions = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        reader.mark(1);
        int firstChar = reader.read();
        if (firstChar != -1 && firstChar != 0xFEFF) {
            reader.reset(); // strip UTF-8 BOM written by Excel
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        int totalRows = 0;
        try (CSVParser parser = new CSVParser(reader, format)) {
            Map<String, Integer> columns = resolveColumns(parser.getHeaderMap());
            ensureRequiredColumns(columns);

            for (CSVRecord record : parser) {
                totalRows++;
                if (totalRows > MAX_ROWS) {
                    throw new BadRequestException("The file contains more than " + MAX_ROWS
                            + " rows. Split it into smaller files and upload again.");
                }
                try {
                    transactions.add(toTransaction(record, columns));
                } catch (IllegalArgumentException ex) {
                    if (errors.size() < 100) {
                        errors.add("Row " + (record.getRecordNumber() + 1) + ": " + ex.getMessage());
                    }
                }
            }
        }

        if (totalRows == 0) {
            throw new BadRequestException("The uploaded file contains no data rows");
        }
        return new Result(transactions, errors, totalRows);
    }

    private Map<String, Integer> resolveColumns(Map<String, Integer> headerMap) {
        if (headerMap == null || headerMap.isEmpty()) {
            throw new BadRequestException("The CSV file has no header row");
        }

        Map<String, Integer> byNormalisedName = new HashMap<>();
        headerMap.forEach((header, index) -> byNormalisedName.putIfAbsent(normalise(header), index));

        Map<String, Integer> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : HEADER_ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                Integer index = byNormalisedName.get(alias);
                if (index != null) {
                    resolved.put(entry.getKey(), index);
                    break;
                }
            }
        }
        return resolved;
    }

    private void ensureRequiredColumns(Map<String, Integer> columns) {
        List<String> missing = REQUIRED_HEADERS.stream()
                .filter(header -> !columns.containsKey(header))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("Missing required column(s): " + String.join(", ", missing)
                    + ". Download the CSV template to see the expected format.");
        }
    }

    private Transaction toTransaction(CSVRecord record, Map<String, Integer> columns) {
        String transactionId = requiredValue(record, columns, "transaction_id", "transaction_id");
        if (transactionId.length() > 30) {
            throw new IllegalArgumentException("transaction_id must be at most 30 characters");
        }

        String orderId = optionalValue(record, columns, "order_id");
        if (orderId != null && orderId.length() > 30) {
            throw new IllegalArgumentException("order_id must be at most 30 characters");
        }

        String gateway = requiredValue(record, columns, "gateway", "gateway");
        if (gateway.length() > 30) {
            throw new IllegalArgumentException("gateway must be at most 30 characters");
        }

        BigDecimal amount = parseAmount(requiredValue(record, columns, "transaction_amount", "transaction_amount"));
        LocalDateTime date = parseDate(requiredValue(record, columns, "transaction_date", "transaction_date"));
        PaymentStatus status = parsePaymentStatus(requiredValue(record, columns, "payment_status", "payment_status"));

        Transaction transaction = new Transaction();
        transaction.setTransactionId(transactionId);
        transaction.setOrderId(orderId);
        transaction.setCustomerName(truncate(optionalValue(record, columns, "customer_name"), 100));
        transaction.setGateway(gateway);
        transaction.setTransactionAmount(amount);
        transaction.setTransactionDate(date);
        transaction.setPaymentStatus(status);
        return transaction;
    }

    private String requiredValue(CSVRecord record, Map<String, Integer> columns, String key, String label) {
        String value = optionalValue(record, columns, key);
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    private String optionalValue(CSVRecord record, Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        if (index == null || record.size() <= index) {
            return null;
        }
        String value = record.get(index);
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    static BigDecimal parseAmount(String raw) {
        String cleaned = raw.replace("₹", "")
                .replace("INR", "")
                .replace("Rs.", "")
                .replace("Rs", "")
                .replace(",", "")
                .replace(" ", "")
                .trim();
        try {
            BigDecimal amount = new BigDecimal(cleaned);
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("transaction_amount cannot be negative");
            }
            return amount.setScale(Math.min(amount.scale(), 2), java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("transaction_amount '" + raw + "' is not a valid number");
        }
    }

    static LocalDateTime parseDate(String raw) {
        String value = raw.trim();
        for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // try the next supported format
            }
        }
        for (DateTimeFormatter formatter : DATE_ONLY_FORMATS) {
            try {
                return LocalDate.parse(value, formatter).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // try the next supported format
            }
        }
        throw new IllegalArgumentException("transaction_date '" + raw
                + "' is not a supported date. Use ISO format, e.g. 2026-06-01 14:30:00");
    }

    static PaymentStatus parsePaymentStatus(String raw) {
        String value = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (value) {
            case "SUCCESS", "SUCCEEDED", "PAID", "CAPTURED", "COMPLETED", "SETTLED" -> PaymentStatus.SUCCESS;
            case "FAILED", "FAILURE", "DECLINED", "CANCELLED", "CANCELED", "ERROR" -> PaymentStatus.FAILED;
            case "PENDING", "PROCESSING", "INITIATED", "CREATED", "AUTHORIZED" -> PaymentStatus.PENDING;
            default -> throw new IllegalArgumentException("payment_status '" + raw
                    + "' is not supported (use SUCCESS, FAILED or PENDING)");
        };
    }

    private static String normalise(String header) {
        if (header == null) {
            return "";
        }
        return header.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
