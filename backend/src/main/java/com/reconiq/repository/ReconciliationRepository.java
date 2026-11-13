package com.reconiq.repository;

import com.reconiq.entity.Reconciliation;
import com.reconiq.entity.ReconciliationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReconciliationRepository extends JpaRepository<Reconciliation, Long> {

    @Query("""
            select r from Reconciliation r
            where (:search is null
                   or lower(coalesce(r.orderId, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(r.transactionId, '')) like lower(concat('%', :search, '%')))
              and (:gateway is null or r.gateway = :gateway)
              and (:status is null or r.reconciliationStatus = :status)
              and (:from is null or r.reconciledAt >= :from)
              and (:to is null or r.reconciledAt < :to)
            """)
    Page<Reconciliation> search(@Param("search") String search,
                                @Param("gateway") String gateway,
                                @Param("status") ReconciliationStatus status,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to,
                                Pageable pageable);

    List<Reconciliation> findAllByOrderByIdAsc();

    @Query("select distinct r.gateway from Reconciliation r where r.gateway is not null order by r.gateway")
    List<String> findDistinctGateways();

    @Query("""
            select r.reconciliationStatus, count(r), coalesce(sum(r.difference), 0)
            from Reconciliation r
            group by r.reconciliationStatus
            """)
    List<Object[]> aggregateByStatus();

    @Query("""
            select r.gateway, count(r),
                   sum(case when r.reconciliationStatus = :matchedStatus then 1 else 0 end),
                   coalesce(sum(r.difference), 0)
            from Reconciliation r
            where r.gateway is not null
            group by r.gateway
            order by count(r) desc
            """)
    List<Object[]> aggregateByGateway(@Param("matchedStatus") ReconciliationStatus matchedStatus);

    long countByReconciliationStatus(ReconciliationStatus status);

    @Query("select coalesce(sum(abs(r.difference)), 0) from Reconciliation r where r.reconciliationStatus <> :matchedStatus")
    java.math.BigDecimal sumAbsoluteDiscrepancy(@Param("matchedStatus") ReconciliationStatus matchedStatus);

    @Query("select max(r.reconciledAt) from Reconciliation r")
    LocalDateTime findLastReconciledAt();
}
