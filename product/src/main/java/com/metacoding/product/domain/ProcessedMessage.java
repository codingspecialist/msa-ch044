package com.metacoding.product.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 멱등성 처리 엔티티
// - messageId 를 PK 로 삼아 중복 메시지 저장 시 DataIntegrityViolationException 발생 → 중복 감지
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "processed_message_tb")
public class ProcessedMessage {
    @Id
    @Column(name = "message_id", length = 100)
    private String messageId;

    @Column(name = "service_name", length = 30)
    private String serviceName;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public ProcessedMessage(String messageId, String serviceName) {
        this.messageId = messageId;
        this.serviceName = serviceName;
        this.processedAt = LocalDateTime.now();
    }
}
