package com.metacoding.delivery.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Event: delivery-created-event (성공/실패 모두 발행)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryCreatedEvent {
    private Integer orderId;
    private Integer deliveryId;
    private boolean success;
}
