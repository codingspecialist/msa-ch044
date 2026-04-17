package com.metacoding.order.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Event: order-created-event (Saga 시작점)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private Integer orderId;
    private Integer userId;
    private Integer productId;
    private Integer quantity;
    private String address;
}
