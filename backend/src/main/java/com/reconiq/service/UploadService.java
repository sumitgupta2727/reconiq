package com.reconiq.service;

import com.reconiq.dto.UploadBatchResponse;
import com.reconiq.entity.Order;
import com.reconiq.entity.Transaction;
import com.reconiq.entity.UploadBatch;
import com.reconiq.exception.BadRequestException;
import com.reconiq.exception.ResourceNotFoundException;
import com.reconiq.repository.OrderRepository;
import com.reconiq.repository.TransactionRepository;
import com.reconiq.repository.UploadBatchRepository;
import com.reconiq.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private final UploadBatchRepository uploadBatchRepository;
    private final TransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final GatewayCsvParser gatewayCsvParser;
    private final OrderCsvParser orderCsvParser;

    public UploadService(UploadBatchRepository uploadBatchRepository,
                         TransactionRepository transactionRepository,
                         OrderRepository orderRepository,
                         UserRepository userRepository,
                         GatewayCsvParser gatewayCsvParser,
                         OrderCsvParser orderCsvParser) {
        this.uploadBatchRepository = uploadBatchRepository;
        this.transactionRepository = transactionRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.gatewayCsvParser = gatewayCsvParser;
        this.orderCsvParser = orderCsvParser;
    }

    /**
     * Parses and stores a gateway CSV. Row-level problems do not abort the import:
     * valid rows are saved and the batch is marked PARTIAL with a summary of the
     * rejected rows, which keeps the audit trail intact.
     */
    // BadRequestException is handled inside the method (the batch is marked FAILED),
    // so the transaction must still commit the audit trail before rethrowing.
    @Transactional(noRollbackFor = BadRequestException.class)
    public UploadBatchResponse upload(MultipartFile file, String uploadedByEmployeeId) {
        validateFile(file);
        String fileName = sanitiseFileName(file.getOriginalFilename());
        UploadBatch batch = startBatch(fileName, UploadBatch.DataType.GATEWAY, uploadedByEmployeeId);

        try {
            GatewayCsvParser.Result result = gatewayCsvParser.parse(file.getInputStream());
            for (Transaction transaction : result.transactions()) {
                transaction.setUploadBatch(batch);
            }
            transactionRepository.saveAll(result.transactions());

            batch.setTotalRecords(result.transactions().size());
            batch.setImportedRecords(result.transactions().size());
            batch.setStatus(result.hasErrors() ? UploadBatch.Status.PARTIAL : UploadBatch.Status.COMPLETED);
            batch.setErrorMessage(result.hasErrors() ? summariseErrors(result.errors()) : null);
            batch = uploadBatchRepository.save(batch);

            log.info("Imported {} transaction(s) from '{}' ({} row(s) rejected)",
                    result.transactions().size(), fileName, result.errors().size());
            return UploadBatchResponse.from(batch);
        } catch (BadRequestException ex) {
            markFailed(batch, ex.getMessage());
            throw ex;
        } catch (IOException ex) {
            markFailed(batch, "The file could not be read: " + ex.getMessage());
            throw new BadRequestException("The uploaded file could not be read. Please upload a valid CSV file.");
        } catch (RuntimeException ex) {
            log.error("Unexpected error while importing '{}'", fileName, ex);
            markFailed(batch, "Unexpected error while importing the file");
            throw new BadRequestException("The uploaded file could not be processed. Please check its contents and try again.");
        }
    }

    /**
     * Imports the business's own orders. Existing {@code order_id}s are updated
     * in place and new ones are created, so the same file can be re-uploaded
     * after a correction without violating the unique order key.
     */
    @Transactional(noRollbackFor = BadRequestException.class)
    public UploadBatchResponse uploadOrders(MultipartFile file, String uploadedByEmployeeId) {
        validateFile(file);
        String fileName = sanitiseFileName(file.getOriginalFilename());
        UploadBatch batch = startBatch(fileName, UploadBatch.DataType.ORDERS, uploadedByEmployeeId);

        try {
            OrderCsvParser.Result result = orderCsvParser.parse(file.getInputStream());
            upsertOrders(result.orders());

            batch.setTotalRecords(result.orders().size());
            batch.setImportedRecords(result.orders().size());
            batch.setStatus(result.hasErrors() ? UploadBatch.Status.PARTIAL : UploadBatch.Status.COMPLETED);
            batch.setErrorMessage(result.hasErrors() ? summariseErrors(result.errors()) : null);
            batch = uploadBatchRepository.save(batch);

            log.info("Imported {} order(s) from '{}' ({} row(s) rejected)",
                    result.orders().size(), fileName, result.errors().size());
            return UploadBatchResponse.from(batch);
        } catch (BadRequestException ex) {
            markFailed(batch, ex.getMessage());
            throw ex;
        } catch (IOException ex) {
            markFailed(batch, "The file could not be read: " + ex.getMessage());
            throw new BadRequestException("The uploaded file could not be read. Please upload a valid CSV file.");
        } catch (RuntimeException ex) {
            log.error("Unexpected error while importing '{}'", fileName, ex);
            markFailed(batch, "Unexpected error while importing the file");
            throw new BadRequestException("The uploaded file could not be processed. Please check its contents and try again.");
        }
    }

    @Transactional(readOnly = true)
    public List<UploadBatchResponse> history() {
        return uploadBatchRepository.findAllByOrderByUploadedAtDesc().stream()
                .map(UploadBatchResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UploadBatchResponse get(Long id) {
        UploadBatch batch = uploadBatchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Upload batch", id));
        return UploadBatchResponse.from(batch);
    }

    /** Template that matches the headers understood by {@link GatewayCsvParser}. */
    public String gatewayTemplate() {
        return String.join("\n",
                "order_id,transaction_id,customer_name,gateway,transaction_amount,transaction_date,payment_status",
                "ORD1001,TXN9001,Aarav Sharma,Razorpay,1500.00,2026-06-01 10:15:00,SUCCESS",
                "ORD1002,TXN9002,Diya Patel,Paytm,1299.00,2026-06-01 10:20:00,SUCCESS",
                "ORD1003,TXN9003,Kabir Singh,Stripe,2399.00,2026-06-01 10:25:00,SUCCESS",
                "");
    }

    /** Template that matches the headers understood by {@link OrderCsvParser}. */
    public String ordersTemplate() {
        return String.join("\n",
                "order_id,customer_name,order_amount,order_date,gateway,status",
                "ORD1001,Aarav Sharma,1500.00,2026-06-01,Razorpay,PAID",
                "ORD1002,Diya Patel,1299.00,2026-06-01,Paytm,PAID",
                "ORD1003,Kabir Singh,2499.00,2026-06-01,Stripe,PAID",
                "");
    }

    /** Backwards-compatible alias for the gateway template. */
    public String csvTemplate() {
        return gatewayTemplate();
    }

    private UploadBatch startBatch(String fileName, UploadBatch.DataType dataType, String uploadedByEmployeeId) {
        UploadBatch batch = new UploadBatch();
        batch.setFileName(fileName);
        batch.setFileType("CSV");
        batch.setDataType(dataType);
        batch.setStatus(UploadBatch.Status.PROCESSING);
        if (uploadedByEmployeeId != null && !uploadedByEmployeeId.isBlank()) {
            userRepository.findByEmployeeIdIgnoreCase(uploadedByEmployeeId.trim())
                    .ifPresent(batch::setUploadedBy);
        }
        return uploadBatchRepository.save(batch);
    }

    private void upsertOrders(List<Order> parsedOrders) {
        if (parsedOrders.isEmpty()) {
            return;
        }

        List<String> orderIds = parsedOrders.stream().map(Order::getOrderId).toList();
        Map<String, Order> existingByOrderId = new HashMap<>();
        for (Order existing : orderRepository.findByOrderIdIn(orderIds)) {
            existingByOrderId.put(existing.getOrderId(), existing);
        }

        List<Order> toSave = new ArrayList<>(parsedOrders.size());
        for (Order incoming : parsedOrders) {
            Order target = existingByOrderId.get(incoming.getOrderId());
            if (target == null) {
                toSave.add(incoming);
            } else {
                target.setCustomerName(incoming.getCustomerName());
                target.setOrderAmount(incoming.getOrderAmount());
                target.setOrderDate(incoming.getOrderDate());
                target.setGateway(incoming.getGateway());
                target.setStatus(incoming.getStatus());
                toSave.add(target);
            }
        }
        orderRepository.saveAll(toSave);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please choose a non-empty CSV file to upload");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new BadRequestException("Only .csv files are supported. Download the template if you need an example.");
        }
    }

    private String sanitiseFileName(String original) {
        if (original == null) {
            return "upload.csv";
        }
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private String summariseErrors(List<String> errors) {
        int shown = Math.min(5, errors.size());
        StringBuilder message = new StringBuilder()
                .append(errors.size())
                .append(" row(s) were skipped. ");
        message.append(String.join("; ", errors.subList(0, shown)));
        if (errors.size() > shown) {
            message.append(" (+").append(errors.size() - shown).append(" more)");
        }
        return message.length() > 500 ? message.substring(0, 497) + "..." : message.toString();
    }

    private void markFailed(UploadBatch batch, String message) {
        batch.setStatus(UploadBatch.Status.FAILED);
        batch.setErrorMessage(message != null && message.length() > 500
                ? message.substring(0, 497) + "..." : message);
        uploadBatchRepository.save(batch);
    }
}
