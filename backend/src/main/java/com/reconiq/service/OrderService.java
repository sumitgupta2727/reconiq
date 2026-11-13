package com.reconiq.service;

import com.reconiq.dto.OrderRequest;
import com.reconiq.dto.OrderResponse;
import com.reconiq.dto.PageResponse;
import com.reconiq.entity.Order;
import com.reconiq.entity.OrderStatus;
import com.reconiq.exception.BadRequestException;
import com.reconiq.exception.ResourceNotFoundException;
import com.reconiq.repository.OrderRepository;
import com.reconiq.web.PageRequests;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class OrderService {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "orderId", "customerName", "orderAmount", "orderDate", "gateway", "status", "createdAt");

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> search(int page, int size, String sortBy, String direction,
                                              String search, OrderStatus status, String gateway) {
        var pageable = PageRequests.of(page, size, sortBy, direction, SORTABLE_FIELDS, "orderDate");
        var result = orderRepository.search(blankToNull(search), status, blankToNull(gateway), pageable)
                .map(OrderResponse::from);
        return PageResponse.of(result);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return OrderResponse.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public List<String> gateways() {
        return orderRepository.findDistinctGateways();
    }

    @Transactional
    public OrderResponse create(OrderRequest request) {
        if (orderRepository.existsByOrderId(request.orderId().trim())) {
            throw new BadRequestException("Order ID '" + request.orderId() + "' already exists");
        }
        Order order = new Order();
        apply(order, request);
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse update(Long id, OrderRequest request) {
        Order order = getEntity(id);
        if (orderRepository.existsByOrderIdAndIdNot(request.orderId().trim(), id)) {
            throw new BadRequestException("Order ID '" + request.orderId() + "' already exists");
        }
        apply(order, request);
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional
    public void delete(Long id) {
        Order order = getEntity(id);
        orderRepository.delete(order);
    }

    private Order getEntity(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    private void apply(Order order, OrderRequest request) {
        order.setOrderId(request.orderId().trim());
        order.setCustomerName(request.customerName().trim());
        order.setOrderAmount(request.orderAmount());
        order.setOrderDate(request.orderDate());
        order.setGateway(blankToNull(request.gateway()));
        order.setStatus(request.status() == null ? OrderStatus.CREATED : request.status());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
