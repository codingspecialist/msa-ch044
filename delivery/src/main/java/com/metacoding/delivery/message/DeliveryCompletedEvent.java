package com.metacoding.delivery.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Event: delivery-completed-event (관리자가 완료 처리했을 때 발행)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryCompletedEvent {
    private Integer orderId;
    private Integer deliveryId;
}
