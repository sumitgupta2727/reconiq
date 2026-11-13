package com.reconiq.service;

import com.reconiq.dto.UploadBatchResponse;
import com.reconiq.entity.Transaction;
import com.reconiq.entity.UploadBatch;
import com.reconiq.exception.BadRequestException;
import com.reconiq.exception.ResourceNotFoundException;
import com.reconiq.repository.TransactionRepository;
import com.reconiq.repository.UploadBatchRepository;
import com.reconiq.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private final UploadBatchRepository uploadBatchRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final GatewayCsvParser csvParser;

    public UploadService(UploadBatchRepository uploadBatchRepository,
                         TransactionRepository transactionRepository,
                         UserRepository userRepository,
                         GatewayCsvParser csvParser) {
        this.uploadBatchRepository = uploadBatchRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.csvParser = csvParser;
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

        UploadBatch batch = new UploadBatch();
        batch.setFileName(fileName);
        batch.setFileType("CSV");
        batch.setStatus(UploadBatch.Status.PROCESSING);
        if (uploadedByEmployeeId != null && !uploadedByEmployeeId.isBlank()) {
            userRepository.findByEmployeeIdIgnoreCase(uploadedByEmployeeId.trim())
                    .ifPresent(batch::setUploadedBy);
        }
        batch = uploadBatchRepository.save(batch);

        try {
            GatewayCsvParser.Result result = csvParser.parse(file.getInputStream());
            for (Transaction transaction : result.transactions()) {
                transaction.setUploadBatch(batch);
            }
            transactionRepository.saveAll(result.transactions());

            batch.setTotalRecords(result.transactions().size());
            batch.setStatus(result.hasErrors() ? UploadBatch.Status.PARTIAL : UploadBatch.Status.COMPLETED);
            batch.setErrorMessage(result.hasErrors() ? summariseErrors(result) : null);
            batch = uploadBatchRepository.save(batch);

            log.info("Imported {} transaction(s) from '{}' ({} row(s) rejected)",
                    result.transactions().size(), fileName, result.errors().size());
            return UploadBatchResponse.from(batch, result.transactions().size());
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
                .map(batch -> UploadBatchResponse.from(batch,
                        transactionRepository.countByUploadBatchId(batch.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public UploadBatchResponse get(Long id) {
        UploadBatch batch = uploadBatchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Upload batch", id));
        return UploadBatchResponse.from(batch, transactionRepository.countByUploadBatchId(batch.getId()));
    }

    /** Template that matches the headers understood by {@link GatewayCsvParser}. */
    public String csvTemplate() {
        return String.join("\n",
                "order_id,transaction_id,customer_name,gateway,transaction_amount,transaction_date,payment_status",
                "ORD1001,TXN9001,Aarav Sharma,Razorpay,1500.00,2026-06-01 10:15:00,SUCCESS",
                "ORD1002,TXN9002,Diya Patel,Paytm,1299.00,2026-06-01 10:20:00,SUCCESS",
                "ORD1003,TXN9003,Kabir Singh,Stripe,2399.00,2026-06-01 10:25:00,SUCCESS",
                "");
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

    private String summariseErrors(GatewayCsvParser.Result result) {
        int shown = Math.min(5, result.errors().size());
        StringBuilder message = new StringBuilder(result.errors().size())
                .append(" row(s) were skipped. ");
        message.append(String.join("; ", result.errors().subList(0, shown)));
        if (result.errors().size() > shown) {
            message.append(" (+").append(result.errors().size() - shown).append(" more)");
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
