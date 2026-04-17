package com.metacoding.order.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Command: order-complete-command (Saga 정상 종료 지시)
// messageId = saga-{orderId}-complete
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCompleteCommand {
    private String messageId;
    private Integer orderId;
}
