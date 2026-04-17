package com.metacoding.orchestrator.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryCreatedEvent {
    private Integer orderId;
    private Integer deliveryId;
    private boolean success;
}
