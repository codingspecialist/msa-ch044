package com.metacoding.product.handler;

import com.metacoding.product.domain.ProcessedMessage;
import com.metacoding.product.domain.Product;
import com.metacoding.product.message.ProductDecreaseCommand;
import com.metacoding.product.message.ProductDecreasedEvent;
import com.metacoding.product.message.ProductIncreaseCommand;
import com.metacoding.product.repository.ProcessedMessageRepository;
import com.metacoding.product.repository.ProductRepository;
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
public class ProductHandler {

    private static final String SERVICE_NAME = "product";

    private final ProductRepository productRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // 재고 차감 명령 수신 → 성공/실패 이벤트 발행
    @KafkaListener(topics = "product-decrease-command", groupId = "product-service")
    @Transactional
    public void onDecrease(ProductDecreaseCommand cmd) {
        // 1) 멱등성 체크
        if (isDuplicate(cmd.getMessageId())) return;

        // 2) 재고 차감
        Product product = productRepository.findById(cmd.getProductId()).orElse(null);
        boolean success = (product != null && product.getQuantity() >= cmd.getQuantity());
        if (success) {
            product.setQuantity(product.getQuantity() - cmd.getQuantity());
            log.info("📦 재고 차감 성공 orderId={} productId={} -{}", cmd.getOrderId(), cmd.getProductId(), cmd.getQuantity());
        } else {
            log.info("⚠ 재고 부족 orderId={} productId={}", cmd.getOrderId(), cmd.getProductId());
        }

        // 3) 이벤트 발행
        kafkaTemplate.send("product-decreased-event",
                String.valueOf(cmd.getOrderId()),
                new ProductDecreasedEvent(cmd.getOrderId(), cmd.getProductId(), success));
    }

    // 재고 복구 명령 수신 (Saga 보상)
    @KafkaListener(topics = "product-increase-command", groupId = "product-service")
    @Transactional
    public void onIncrease(ProductIncreaseCommand cmd) {
        if (isDuplicate(cmd.getMessageId())) return;

        Product product = productRepository.findById(cmd.getProductId()).orElse(null);
        if (product != null) {
            product.setQuantity(product.getQuantity() + cmd.getQuantity());
            log.info("♻ 재고 복구 orderId={} productId={} +{}", cmd.getOrderId(), cmd.getProductId(), cmd.getQuantity());
        }
    }

    // 멱등성 검사: messageId 저장 시도 → 중복이면 true 리턴
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
