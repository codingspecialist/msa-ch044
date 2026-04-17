package com.metacoding.order.web;

import com.metacoding.order.domain.Order;
import com.metacoding.order.message.OrderCreatedEvent;
import com.metacoding.order.repository.OrderRepository;
import com.metacoding.order.util.JwtVerifier;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;
    private final JwtVerifier jwtVerifier;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // 주문 생성: JWT 필요
    @PostMapping("/api/orders")
    public Order create(@RequestHeader("Authorization") String auth,
                        @RequestBody OrderRequest req) {
        Integer userId = authenticate(auth);

        // 1) 주문 저장 (status=PENDING)
        Order order = new Order();
        order.setUserId(userId);
        order.setProductId(req.getProductId());
        order.setQuantity(req.getQuantity());
        order.setStatus("PENDING");
        orderRepository.save(order);

        // 2) Saga 시작 이벤트 발행
        kafkaTemplate.send("order-created-event",
                String.valueOf(order.getId()),
                new OrderCreatedEvent(order.getId(), userId, req.getProductId(), req.getQuantity(), req.getAddress()));
        return order;
    }

    // 내 주문 목록: JWT 필요
    @GetMapping("/api/orders/my")
    public List<Order> myOrders(@RequestHeader("Authorization") String auth) {
        Integer userId = authenticate(auth);
        return orderRepository.findByUserIdOrderByIdDesc(userId);
    }

    private Integer authenticate(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰 없음");
        }
        Integer userId = jwtVerifier.verifyAndGetUserId(authHeader.substring(7));
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰 무효");
        }
        return userId;
    }

    @Data
    public static class OrderRequest {
        private Integer productId;
        private Integer quantity;
        private String address;
    }
}
