package com.reconiq.service;

import com.reconiq.dto.PageResponse;
import com.reconiq.dto.TransactionRequest;
import com.reconiq.dto.TransactionResponse;
import com.reconiq.entity.PaymentStatus;
import com.reconiq.entity.Transaction;
import com.reconiq.exception.ResourceNotFoundException;
import com.reconiq.repository.TransactionRepository;
import com.reconiq.web.PageRequests;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class TransactionService {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "transactionId", "orderId", "customerName", "gateway",
                    "transactionAmount", "transactionDate", "paymentStatus", "createdAt");

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> search(int page, int size, String sortBy, String direction,
                                                    String search, String gateway, PaymentStatus status,
                                                    LocalDate from, LocalDate to) {
        var pageable = PageRequests.of(page, size, sortBy, direction, SORTABLE_FIELDS, "transactionDate");
        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();
        var result = transactionRepository
                .search(blankToNull(search), blankToNull(gateway), status, fromTime, toTime, pageable)
                .map(TransactionResponse::from);
        return PageResponse.of(result);
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(Long id) {
        return TransactionResponse.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public List<String> gateways() {
        return transactionRepository.findDistinctGateways();
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        Transaction transaction = new Transaction();
        apply(transaction, request);
        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionResponse update(Long id, TransactionRequest request) {
        Transaction transaction = getEntity(id);
        apply(transaction, request);
        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    @Transactional
    public void delete(Long id) {
        transactionRepository.delete(getEntity(id));
    }

    private Transaction getEntity(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));
    }

    private void apply(Transaction transaction, TransactionRequest request) {
        transaction.setTransactionId(request.transactionId().trim());
        transaction.setOrderId(blankToNull(request.orderId()));
        transaction.setCustomerName(blankToNull(request.customerName()));
        transaction.setGateway(request.gateway().trim());
        transaction.setTransactionAmount(request.transactionAmount());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setPaymentStatus(request.paymentStatus());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
