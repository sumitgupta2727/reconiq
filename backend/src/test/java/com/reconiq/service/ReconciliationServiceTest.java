package com.reconiq.service;

import com.reconiq.entity.Order;
import com.reconiq.entity.PaymentStatus;
import com.reconiq.entity.Reconciliation;
import com.reconiq.entity.ReconciliationStatus;
import com.reconiq.entity.Transaction;
import com.reconiq.repository.OrderRepository;
import com.reconiq.repository.ReconciliationRepository;
import com.reconiq.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ReconciliationRepository reconciliationRepository;

    @InjectMocks
    private ReconciliationService reconciliationService;

    @Test
    void classifiesEverySupportedStatus() {
        List<Order> orders = List.of(
                order(1L, "ORD1", "1500.00"),
                order(2L, "ORD2", "1000.00"),
                order(3L, "ORD3", "500.00"),
                order(4L, "ORD4", "200.00"),
                order(5L, "ORD5", "300.00"),
                order(6L, "ORD6", "700.00"),
                order(7L, "ORD7", "250.00"));

        List<Transaction> transactions = List.of(
                transaction(1L, "T1", "ORD1", "1500.00", PaymentStatus.SUCCESS),
                transaction(2L, "T2", "ORD2", "900.00", PaymentStatus.SUCCESS),
                transaction(3L, "T3", "ORD3", "500.00", PaymentStatus.FAILED),
                transaction(4L, "T4", "ORD4", "200.00", PaymentStatus.PENDING),
                transaction(5L, "T5", "ORD5", "300.00", PaymentStatus.SUCCESS),
                transaction(6L, "T5", "ORD5", "300.00", PaymentStatus.SUCCESS), // duplicate
                transaction(7L, "T7", "ORD7", "250.00", PaymentStatus.SUCCESS),
                transaction(8L, "T8", "ORD999", "400.00", PaymentStatus.SUCCESS)); // unknown order

        when(orderRepository.findAll()).thenReturn(orders);
        when(transactionRepository.findAll()).thenReturn(transactions);
        when(reconciliationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var summary = reconciliationService.run();

        assertThat(summary.totalRecords()).isEqualTo(9);
        assertThat(summary.matched()).isEqualTo(3);
        assertThat(summary.unmatched()).isEqualTo(1);
        assertThat(summary.amountMismatch()).isEqualTo(1);
        assertThat(summary.duplicate()).isEqualTo(1);
        assertThat(summary.missing()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.pending()).isEqualTo(1);
        assertThat(summary.matchedAmount()).isEqualByComparingTo("2050.00");
        assertThat(summary.discrepancyAmount()).isEqualByComparingTo("1200.00");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Reconciliation>> captor = ArgumentCaptor.forClass(List.class);
        verify(reconciliationRepository).saveAll(captor.capture());
        Map<String, List<ReconciliationStatus>> statusesByOrder = captor.getValue().stream()
                .collect(Collectors.groupingBy(Reconciliation::getOrderId,
                        Collectors.mapping(Reconciliation::getReconciliationStatus, Collectors.toList())));

        assertThat(statusesByOrder)
                .containsEntry("ORD1", List.of(ReconciliationStatus.MATCHED))
                .containsEntry("ORD2", List.of(ReconciliationStatus.AMOUNT_MISMATCH))
                .containsEntry("ORD3", List.of(ReconciliationStatus.FAILED))
                .containsEntry("ORD4", List.of(ReconciliationStatus.PENDING))
                .containsEntry("ORD5", List.of(ReconciliationStatus.MATCHED, ReconciliationStatus.DUPLICATE))
                .containsEntry("ORD6", List.of(ReconciliationStatus.MISSING))
                .containsEntry("ORD7", List.of(ReconciliationStatus.MATCHED))
                .containsEntry("ORD999", List.of(ReconciliationStatus.UNMATCHED));
    }

    @Test
    void computesSignedDifferenceAndMissingAmount() {
        Order order = order(1L, "ORD1", "1500.00");
        when(orderRepository.findAll()).thenReturn(List.of(order));
        when(transactionRepository.findAll()).thenReturn(List.of());
        when(reconciliationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        reconciliationService.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Reconciliation>> captor = ArgumentCaptor.forClass(List.class);
        verify(reconciliationRepository).saveAll(captor.capture());
        Reconciliation result = captor.getValue().get(0);

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.MISSING);
        assertThat(result.getDifference()).isEqualByComparingTo("-1500.00");
        assertThat(result.getTransactionAmount()).isNull();
    }

    @Test
    void prefersSuccessfulExactAmountAsPrimaryPayment() {
        Order order = order(1L, "ORD1", "1000.00");
        Transaction pending = transaction(1L, "T1", "ORD1", "1000.00", PaymentStatus.PENDING);
        Transaction success = transaction(2L, "T2", "ORD1", "1000.00", PaymentStatus.SUCCESS);

        when(orderRepository.findAll()).thenReturn(List.of(order));
        when(transactionRepository.findAll()).thenReturn(List.of(pending, success));
        when(reconciliationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        reconciliationService.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Reconciliation>> captor = ArgumentCaptor.forClass(List.class);
        verify(reconciliationRepository).saveAll(captor.capture());
        List<Reconciliation> results = captor.getValue();

        assertThat(results).hasSize(2);
        assertThat(results).anyMatch(r -> r.getReconciliationStatus() == ReconciliationStatus.MATCHED
                && "T2".equals(r.getTransactionId()));
        assertThat(results).anyMatch(r -> r.getReconciliationStatus() == ReconciliationStatus.DUPLICATE
                && "T1".equals(r.getTransactionId()));
    }

    private static Order order(Long id, String orderId, String amount) {
        Order order = new Order();
        order.setId(id);
        order.setOrderId(orderId);
        order.setCustomerName("Customer " + orderId);
        order.setOrderAmount(new BigDecimal(amount));
        order.setOrderDate(LocalDate.now().minusDays(1));
        order.setGateway("Razorpay");
        return order;
    }

    private static Transaction transaction(Long id, String transactionId, String orderId,
                                          String amount, PaymentStatus status) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setTransactionId(transactionId);
        transaction.setOrderId(orderId);
        transaction.setGateway("Razorpay");
        transaction.setTransactionAmount(new BigDecimal(amount));
        transaction.setTransactionDate(java.time.LocalDateTime.now().minusDays(1));
        transaction.setPaymentStatus(status);
        return transaction;
    }
}
