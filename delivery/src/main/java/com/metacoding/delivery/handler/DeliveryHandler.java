package com.metacoding.delivery.handler;

import com.metacoding.delivery.domain.Delivery;
import com.metacoding.delivery.domain.ProcessedMessage;
import com.metacoding.delivery.message.DeliveryCreateCommand;
import com.metacoding.delivery.message.DeliveryCreatedEvent;
import com.metacoding.delivery.repository.DeliveryRepository;
import com.metacoding.delivery.repository.ProcessedMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryHandler {

    private static final String SERVICE_NAME = "delivery";

    private final DeliveryRepository deliveryRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // 배송 생성 명령 수신 → 검증 후 저장 (validate-first)
    @KafkaListener(topics = "delivery-create-command", groupId = "delivery-service")
    @Transactional
    public void onCreate(DeliveryCreateCommand cmd) {
        if (isDuplicate(cmd.getMessageId())) return;

        // 주소 검증 먼저 (validate-first 패턴)
        boolean valid = (cmd.getAddress() != null && !cmd.getAddress().isBlank());
        Integer deliveryId = null;

        if (valid) {
            Delivery delivery = new Delivery();
            delivery.setOrderId(cmd.getOrderId());
            delivery.setAddress(cmd.getAddress());
            delivery.setStatus("READY");
            deliveryRepository.save(delivery);
            deliveryId = delivery.getId();
            log.info("🚚 배송 생성 orderId={} deliveryId={}", cmd.getOrderId(), deliveryId);
        } else {
            log.info("⚠ 주소 검증 실패 orderId={}", cmd.getOrderId());
        }

        kafkaTemplate.send("delivery-created-event",
                String.valueOf(cmd.getOrderId()),
                new DeliveryCreatedEvent(cmd.getOrderId(), deliveryId, valid));
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
