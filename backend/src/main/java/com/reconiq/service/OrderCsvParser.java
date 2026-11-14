package com.reconiq.service;

import com.reconiq.entity.Order;
import com.reconiq.entity.OrderStatus;
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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses the business's own orders CSV without touching the database.
 *
 * <p>Header names are normalised, so both {@code ORDER ID} and {@code order_id}
 * are accepted. Invalid rows are reported individually and skipped, while a
 * duplicated {@code order_id} inside the same file is rejected because only one
 * row per order can be applied.</p>
 */
@Component
public class OrderCsvParser {

    public static final int MAX_ROWS = 50_000;

    private static final Map<String, Set<String>> HEADER_ALIASES = Map.of(
            "order_id", Set.of("order_id", "orderid", "order_no", "order_number", "order"),
            "customer_name", Set.of("customer_name", "customername", "customer", "name"),
            "order_amount", Set.of("order_amount", "orderamount", "amount", "order_total",
                    "ordertotal", "total", "value"),
            "order_date", Set.of("order_date", "orderdate", "date", "created_at", "createdat"),
            "gateway", Set.of("gateway", "payment_gateway", "provider", "gateway_name"),
            "status", Set.of("status", "order_status", "orderstatus", "state")
    );

    private static final Set<String> REQUIRED_HEADERS =
            Set.of("order_id", "customer_name", "order_amount", "order_date");

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

    public record Result(List<Order> orders, List<String> errors, int totalRows) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    public Result parse(InputStream inputStream) throws IOException {
        List<Order> orders = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> seenOrderIds = new HashSet<>();

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
                    Order order = toOrder(record, columns);
                    if (!seenOrderIds.add(order.getOrderId())) {
                        throw new IllegalArgumentException("order_id '" + order.getOrderId()
                                + "' is duplicated in this file; keep only one row per order");
                    }
                    orders.add(order);
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
        return new Result(orders, errors, totalRows);
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
                    + ". Download the orders CSV template to see the expected format.");
        }
    }

    private Order toOrder(CSVRecord record, Map<String, Integer> columns) {
        String orderId = requiredValue(record, columns, "order_id", "order_id");
        if (orderId.length() > 30) {
            throw new IllegalArgumentException("order_id must be at most 30 characters");
        }

        String customerName = requiredValue(record, columns, "customer_name", "customer_name");
        if (customerName.length() > 100) {
            throw new IllegalArgumentException("customer_name must be at most 100 characters");
        }

        String gateway = optionalValue(record, columns, "gateway");
        if (gateway != null && gateway.length() > 30) {
            throw new IllegalArgumentException("gateway must be at most 30 characters");
        }

        BigDecimal amount = parseAmount(requiredValue(record, columns, "order_amount", "order_amount"));
        LocalDate date = parseDate(requiredValue(record, columns, "order_date", "order_date"));
        String rawStatus = optionalValue(record, columns, "status");
        OrderStatus status = rawStatus == null ? OrderStatus.CREATED : parseOrderStatus(rawStatus);

        Order order = new Order();
        order.setOrderId(orderId);
        order.setCustomerName(customerName);
        order.setOrderAmount(amount);
        order.setOrderDate(date);
        order.setGateway(gateway);
        order.setStatus(status);
        return order;
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
                throw new IllegalArgumentException("order_amount cannot be negative");
            }
            return amount.setScale(Math.min(amount.scale(), 2), RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("order_amount '" + raw + "' is not a valid number");
        }
    }

    static LocalDate parseDate(String raw) {
        String value = raw.trim();
        for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
            try {
                return java.time.LocalDateTime.parse(value, formatter).toLocalDate();
            } catch (DateTimeParseException ignored) {
                // try the next supported format
            }
        }
        for (DateTimeFormatter formatter : DATE_ONLY_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // try the next supported format
            }
        }
        throw new IllegalArgumentException("order_date '" + raw
                + "' is not a supported date. Use ISO format, e.g. 2026-06-01");
    }

    static OrderStatus parseOrderStatus(String raw) {
        String value = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (value) {
            case "PAID", "SUCCESS", "SUCCEEDED", "COMPLETED", "CAPTURED", "SETTLED" -> OrderStatus.PAID;
            case "CREATED", "PENDING", "PROCESSING", "INITIATED", "OPEN", "AUTHORIZED" -> OrderStatus.CREATED;
            case "CANCELLED", "CANCELED", "VOID", "REFUNDED", "FAILED", "EXPIRED" -> OrderStatus.CANCELLED;
            default -> throw new IllegalArgumentException("status '" + raw
                    + "' is not supported (use PAID, CREATED or CANCELLED)");
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
}
