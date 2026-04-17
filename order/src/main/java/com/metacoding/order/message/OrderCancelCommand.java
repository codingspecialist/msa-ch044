package com.metacoding.order.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Command: order-cancel-command (Saga 보상 종료 지시)
// messageId = saga-{orderId}-cancel
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelCommand {
    private String messageId;
    private Integer orderId;
}
