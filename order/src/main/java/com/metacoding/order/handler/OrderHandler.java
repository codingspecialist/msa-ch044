package com.metacoding.order.handler;

import com.metacoding.order.domain.Order;
import com.metacoding.order.domain.ProcessedMessage;
import com.metacoding.order.message.OrderCancelCommand;
import com.metacoding.order.message.OrderCompleteCommand;
import com.metacoding.order.repository.OrderRepository;
import com.metacoding.order.repository.ProcessedMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderHandler {

    private static final String SERVICE_NAME = "order";

    private final OrderRepository orderRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // 주문 완료 명령 수신 → 주문 상태 COMPLETED + 웹소켓 푸시
    @KafkaListener(topics = "order-complete-command", groupId = "order-service")
    @Transactional
    public void onComplete(OrderCompleteCommand cmd) {
        if (isDuplicate(cmd.getMessageId())) return;

        Order order = orderRepository.findById(cmd.getOrderId()).orElse(null);
        if (order == null) return;
        order.setStatus("COMPLETED");
        log.info("✅ 주문 완료 orderId={}", order.getId());

        pushToUser(order, "주문이 완료되었습니다.");
    }

    // 주문 취소 명령 수신 → 주문 상태 CANCELLED + 웹소켓 푸시
    @KafkaListener(topics = "order-cancel-command", groupId = "order-service")
    @Transactional
    public void onCancel(OrderCancelCommand cmd) {
        if (isDuplicate(cmd.getMessageId())) return;

        Order order = orderRepository.findById(cmd.getOrderId()).orElse(null);
        if (order == null) return;
        order.setStatus("CANCELLED");
        log.info("❌ 주문 취소 orderId={}", order.getId());

        pushToUser(order, "재고 부족으로 주문이 취소되었습니다.");
    }

    // 사용자별 topic 으로 푸시 (/topic/orders/{userId})
    private void pushToUser(Order order, String message) {
        messagingTemplate.convertAndSend(
                "/topic/orders/" + order.getUserId(),
                Map.of(
                        "orderId", order.getId(),
                        "status", order.getStatus(),
                        "message", message
                )
        );
    }

    private boolean isDuplicate(String messageId) {
        try {
            processedMessageRepository.save(new ProcessedMessage(messageId, SERVICE_NAME));
            return false;
        } catch (DataIntegrityViolationException e) {
            log.info("🔁 중복 메시지 스킵: {}", messageId);
            return true;
        }
    }
}
