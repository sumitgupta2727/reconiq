package com.reconiq.repository;

import com.reconiq.entity.PaymentStatus;
import com.reconiq.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("""
            select t from Transaction t
            where (:search is null
                   or lower(t.transactionId) like lower(concat('%', :search, '%'))
                   or lower(coalesce(t.orderId, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(t.customerName, '')) like lower(concat('%', :search, '%')))
              and (:gateway is null or t.gateway = :gateway)
              and (:status is null or t.paymentStatus = :status)
              and (:from is null or t.transactionDate >= :from)
              and (:to is null or t.transactionDate < :to)
            """)
    Page<Transaction> search(@Param("search") String search,
                             @Param("gateway") String gateway,
                             @Param("status") PaymentStatus status,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to,
                             Pageable pageable);

    @Query("select distinct t.gateway from Transaction t order by t.gateway")
    List<String> findDistinctGateways();

    long countByUploadBatchId(Long uploadBatchId);

    long countByPaymentStatus(PaymentStatus paymentStatus);

    @Query("select coalesce(sum(t.transactionAmount), 0) from Transaction t where t.paymentStatus = :status")
    BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);

    @Query("select coalesce(sum(t.transactionAmount), 0) from Transaction t")
    BigDecimal sumTransactionAmount();
}
