package com.metacoding.delivery.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
