package com.reconiq.service;

import com.reconiq.dto.PageResponse;
import com.reconiq.dto.ReconciliationResponse;
import com.reconiq.dto.ReconciliationSummary;
import com.reconiq.entity.Order;
import com.reconiq.entity.PaymentStatus;
import com.reconiq.entity.Reconciliation;
import com.reconiq.entity.ReconciliationStatus;
import com.reconiq.entity.Transaction;
import com.reconiq.exception.ResourceNotFoundException;
import com.reconiq.repository.OrderRepository;
import com.reconiq.repository.ReconciliationRepository;
import com.reconiq.repository.TransactionRepository;
import com.reconiq.web.PageRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The reconciliation engine.
 *
 * <p>Matching rule (from the project specification): an order is MATCHED only
 * when the order ID and transaction ID are linked and the amounts are exactly
 * equal. Every other case is classified as MISSING, DUPLICATE, FAILED, PENDING,
 * AMOUNT_MISMATCH or UNMATCHED.</p>
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "orderId", "transactionId", "gateway", "orderAmount",
                    "transactionAmount", "difference", "reconciliationStatus", "reconciledAt");

    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final ReconciliationRepository reconciliationRepository;

    public ReconciliationService(OrderRepository orderRepository,
                                 TransactionRepository transactionRepository,
                                 ReconciliationRepository reconciliationRepository) {
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
        this.reconciliationRepository = reconciliationRepository;
    }

    @Transactional
    public ReconciliationSummary run() {
        long startedAt = System.currentTimeMillis();
        LocalDateTime runAt = LocalDateTime.now();

        reconciliationRepository.deleteAllInBatch();

        List<Order> orders = orderRepository.findAll();
        List<Transaction> transactions = transactionRepository.findAll();

        Map<String, List<Transaction>> transactionsByOrder = new HashMap<>();
        for (Transaction transaction : transactions) {
            String orderId = transaction.getOrderId();
            if (orderId != null && !orderId.isBlank()) {
                transactionsByOrder
                        .computeIfAbsent(orderId.trim(), key -> new ArrayList<>())
                        .add(transaction);
            }
        }

        Set<Long> consumedTransactionIds = new HashSet<>();
        List<Reconciliation> results = new ArrayList<>();

        for (Order order : orders) {
            List<Transaction> candidates = transactionsByOrder.getOrDefault(order.getOrderId(), List.of());
            if (candidates.isEmpty()) {
                results.add(missing(order));
                continue;
            }

            Transaction primary = pickPrimary(order, candidates);
            for (Transaction candidate : candidates) {
                consumedTransactionIds.add(candidate.getId());
            }

            results.add(classify(order, primary));
            for (Transaction candidate : candidates) {
                if (!candidate.getId().equals(primary.getId())) {
                    results.add(duplicate(order, candidate));
                }
            }
        }

        for (Transaction transaction : transactions) {
            if (!consumedTransactionIds.contains(transaction.getId())) {
                results.add(unmatched(transaction));
            }
        }

        reconciliationRepository.saveAll(results);

        ReconciliationSummary summary = summarise(results, runAt, System.currentTimeMillis() - startedAt);
        log.info("Reconciliation finished: {} records, {} matched, {} issues in {} ms",
                summary.totalRecords(), summary.matched(),
                summary.totalRecords() - summary.matched(), summary.durationMs());
        return summary;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReconciliationResponse> search(int page, int size, String sortBy, String direction,
                                                       String search, String gateway,
                                                       ReconciliationStatus status,
                                                       LocalDate from, LocalDate to) {
        var pageable = PageRequests.of(page, size, sortBy, direction, SORTABLE_FIELDS, "id");
        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();
        var result = reconciliationRepository
                .search(blankToNull(search), blankToNull(gateway), status, fromTime, toTime, pageable)
                .map(ReconciliationResponse::from);
        return PageResponse.of(result);
    }

    @Transactional(readOnly = true)
    public ReconciliationResponse get(Long id) {
        return reconciliationRepository.findById(id)
                .map(ReconciliationResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Reconciliation result", id));
    }

    @Transactional(readOnly = true)
    public List<String> gateways() {
        return reconciliationRepository.findDistinctGateways();
    }

    /**
     * Prefers a successful transaction with the exact expected amount, so a
     * stray duplicate does not mask the real payment. Falls back to the first
     * transaction of the order.
     */
    private Transaction pickPrimary(Order order, List<Transaction> candidates) {
        return candidates.stream()
                .filter(t -> t.getPaymentStatus() == PaymentStatus.SUCCESS)
                .filter(t -> isSameAmount(order.getOrderAmount(), t.getTransactionAmount()))
                .findFirst()
                .or(() -> candidates.stream()
                        .filter(t -> t.getPaymentStatus() == PaymentStatus.SUCCESS)
                        .findFirst())
                .orElse(candidates.get(0));
    }

    private Reconciliation classify(Order order, Transaction transaction) {
        Reconciliation result = base(order, transaction);
        result.setOrderAmount(order.getOrderAmount());
        result.setTransactionAmount(transaction.getTransactionAmount());
        result.setDifference(difference(order.getOrderAmount(), transaction.getTransactionAmount()));

        if (transaction.getPaymentStatus() == PaymentStatus.FAILED) {
            result.setReconciliationStatus(ReconciliationStatus.FAILED);
        } else if (transaction.getPaymentStatus() == PaymentStatus.PENDING) {
            result.setReconciliationStatus(ReconciliationStatus.PENDING);
        } else if (result.getDifference().signum() != 0) {
            result.setReconciliationStatus(ReconciliationStatus.AMOUNT_MISMATCH);
        } else {
            result.setReconciliationStatus(ReconciliationStatus.MATCHED);
        }
        return result;
    }

    private Reconciliation missing(Order order) {
        Reconciliation result = new Reconciliation();
        result.setOrder(order);
        result.setOrderId(order.getOrderId());
        result.setGateway(order.getGateway());
        result.setOrderAmount(order.getOrderAmount());
        result.setDifference(order.getOrderAmount() == null
                ? BigDecimal.ZERO : order.getOrderAmount().negate());
        result.setReconciliationStatus(ReconciliationStatus.MISSING);
        return result;
    }

    private Reconciliation duplicate(Order order, Transaction transaction) {
        Reconciliation result = base(order, transaction);
        result.setOrderAmount(order.getOrderAmount());
        result.setTransactionAmount(transaction.getTransactionAmount());
        result.setDifference(difference(order.getOrderAmount(), transaction.getTransactionAmount()));
        result.setReconciliationStatus(ReconciliationStatus.DUPLICATE);
        return result;
    }

    private Reconciliation unmatched(Transaction transaction) {
        Reconciliation result = new Reconciliation();
        result.setTransaction(transaction);
        result.setTransactionId(transaction.getTransactionId());
        result.setOrderId(transaction.getOrderId());
        result.setGateway(transaction.getGateway());
        result.setTransactionAmount(transaction.getTransactionAmount());
        result.setDifference(transaction.getTransactionAmount() == null
                ? BigDecimal.ZERO : transaction.getTransactionAmount());
        result.setReconciliationStatus(ReconciliationStatus.UNMATCHED);
        return result;
    }

    private Reconciliation base(Order order, Transaction transaction) {
        Reconciliation result = new Reconciliation();
        result.setOrder(order);
        result.setTransaction(transaction);
        result.setOrderId(order.getOrderId());
        result.setTransactionId(transaction.getTransactionId());
        result.setGateway(order.getGateway() != null ? order.getGateway() : transaction.getGateway());
        return result;
    }

    private static BigDecimal difference(BigDecimal orderAmount, BigDecimal transactionAmount) {
        if (orderAmount == null && transactionAmount == null) {
            return BigDecimal.ZERO;
        }
        if (orderAmount == null) {
            return transactionAmount;
        }
        if (transactionAmount == null) {
            return orderAmount.negate();
        }
        return orderAmount.subtract(transactionAmount);
    }

    private static boolean isSameAmount(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private ReconciliationSummary summarise(List<Reconciliation> results, LocalDateTime runAt, long durationMs) {
        long matched = 0;
        long unmatched = 0;
        long mismatch = 0;
        long duplicate = 0;
        long missing = 0;
        long failed = 0;
        long pending = 0;
        BigDecimal matchedAmount = BigDecimal.ZERO;
        BigDecimal discrepancy = BigDecimal.ZERO;

        for (Reconciliation result : results) {
            switch (result.getReconciliationStatus()) {
                case MATCHED -> {
                    matched++;
                    if (result.getOrderAmount() != null) {
                        matchedAmount = matchedAmount.add(result.getOrderAmount());
                    }
                }
                case UNMATCHED -> unmatched++;
                case AMOUNT_MISMATCH -> mismatch++;
                case DUPLICATE -> duplicate++;
                case MISSING -> missing++;
                case FAILED -> failed++;
                case PENDING -> pending++;
            }
            if (result.getReconciliationStatus() != ReconciliationStatus.MATCHED
                    && result.getDifference() != null) {
                discrepancy = discrepancy.add(result.getDifference().abs());
            }
        }

        return new ReconciliationSummary(runAt, results.size(), matched, unmatched, mismatch,
                duplicate, missing, failed, pending, matchedAmount, discrepancy, durationMs);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
