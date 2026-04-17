package com.metacoding.orchestrator.handler;

import com.metacoding.orchestrator.message.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saga 중앙 조율자 (Orchestrator)
 *
 * 핵심 규칙 2가지:
 *  1) 모든 command 에는 결정적 messageId 부여 → 소비측 멱등성과 짝지음
 *     - saga-{orderId}-decrease         (재고 차감)
 *     - saga-{orderId}-increase         (재고 복구, 보상)
 *     - saga-{orderId}-create-delivery  (배송 생성)
 *     - saga-{orderId}-complete         (주문 완료)
 *     - saga-{orderId}-cancel           (주문 취소, 보상)
 *
 *  2) 주문 1건 = 상품 1개 (교육용 단순화) → 상품별 fan-out / join 불필요
 *
 * ⚠ 프로덕션 필요: state persistence (지금은 in-memory Map)
 * ⚠ 프로덕션 필요: Transactional Outbox (Kafka 전송 실패 시 보상)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOrchestrator {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // 주문 ID → 워크플로우 상태 (address 포함)
    private final Map<Integer, WorkflowState> states = new ConcurrentHashMap<>();

    // 1) 주문 생성 → 재고 차감 명령
    @KafkaListener(topics = "order-created-event", groupId = "orchestrator")
    public void onOrderCreated(OrderCreatedEvent event) {
        int orderId = event.getOrderId();
        states.put(orderId, new WorkflowState(orderId, event.getProductId(), event.getQuantity(), event.getAddress()));
        log.info("🎯 Saga 시작 orderId={} productId={}", orderId, event.getProductId());

        send("product-decrease-command", orderId,
                new ProductDecreaseCommand("saga-" + orderId + "-decrease", orderId, event.getProductId(), event.getQuantity()));
    }

    // 2) 재고 차감 결과 → 성공: 배송 생성 / 실패: 주문 취소
    @KafkaListener(topics = "product-decreased-event", groupId = "orchestrator")
    public void onProductDecreased(ProductDecreasedEvent event) {
        int orderId = event.getOrderId();
        WorkflowState state = states.get(orderId);
        if (state == null) return;

        if (event.isSuccess()) {
            log.info("✅ 재고 차감 성공 → 배송 생성 orderId={}", orderId);
            send("delivery-create-command", orderId,
                    new DeliveryCreateCommand("saga-" + orderId + "-create-delivery", orderId, state.getAddress()));
        } else {
            log.info("❌ 재고 차감 실패 → 주문 취소 orderId={}", orderId);
            send("order-cancel-command", orderId,
                    new OrderCancelCommand("saga-" + orderId + "-cancel", orderId));
            states.remove(orderId);
        }
    }

    // 3) 배송 생성 결과 → 성공: 대기 / 실패: 재고 복구 + 주문 취소
    @KafkaListener(topics = "delivery-created-event", groupId = "orchestrator")
    public void onDeliveryCreated(DeliveryCreatedEvent event) {
        int orderId = event.getOrderId();
        WorkflowState state = states.get(orderId);
        if (state == null) return;

        if (event.isSuccess()) {
            log.info("🚚 배송 생성 성공 → 배송 완료 API 대기 orderId={}", orderId);
            return; // 배송 완료까지 state 유지 (주소는 필요 없어 지우고 싶다면 지워도 됨)
        }

        log.info("❌ 배송 생성 실패 → 재고 복구 + 주문 취소 orderId={}", orderId);
        send("product-increase-command", orderId,
                new ProductIncreaseCommand("saga-" + orderId + "-increase", orderId, state.getProductId(), state.getQuantity()));
        send("order-cancel-command", orderId,
                new OrderCancelCommand("saga-" + orderId + "-cancel", orderId));
        states.remove(orderId);
    }

    // 4) 배송 완료 → 주문 완료 명령
    @KafkaListener(topics = "delivery-completed-event", groupId = "orchestrator")
    public void onDeliveryCompleted(DeliveryCompletedEvent event) {
        int orderId = event.getOrderId();
        log.info("🏁 배송 완료 → 주문 완료 orderId={}", orderId);
        send("order-complete-command", orderId,
                new OrderCompleteCommand("saga-" + orderId + "-complete", orderId));
        states.remove(orderId);
    }

    private void send(String topic, int orderId, Object payload) {
        kafkaTemplate.send(topic, String.valueOf(orderId), payload);
    }

    @Data
    private static class WorkflowState {
        private final int orderId;
        private final Integer productId;
        private final Integer quantity;
        private final String address;
    }
}
