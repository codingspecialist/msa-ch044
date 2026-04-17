package com.metacoding.product.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Command: product-decrease-command
// messageId = saga-{orderId}-decrease (orchestrator 가 결정적으로 생성)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDecreaseCommand {
    private String messageId;
    private Integer orderId;
    private Integer productId;
    private Integer quantity;
}
