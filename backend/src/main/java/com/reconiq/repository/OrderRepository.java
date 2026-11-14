package com.reconiq.repository;

import com.reconiq.entity.Order;
import com.reconiq.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderId(String orderId);

    List<Order> findByOrderIdIn(Collection<String> orderIds);

    boolean existsByOrderId(String orderId);

    boolean existsByOrderIdAndIdNot(String orderId, Long id);

    @Query("""
            select o from Order o
            where (:search is null
                   or lower(o.orderId) like lower(concat('%', :search, '%'))
                   or lower(o.customerName) like lower(concat('%', :search, '%')))
              and (:status is null or o.status = :status)
              and (:gateway is null or o.gateway = :gateway)
            """)
    Page<Order> search(@Param("search") String search,
                       @Param("status") OrderStatus status,
                       @Param("gateway") String gateway,
                       Pageable pageable);

    @Query("select distinct o.gateway from Order o where o.gateway is not null order by o.gateway")
    List<String> findDistinctGateways();

    @Query("select coalesce(sum(o.orderAmount), 0) from Order o")
    BigDecimal sumOrderAmount();
}
