package com.metacoding.product.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Command (보상): product-increase-command
// messageId = saga-{orderId}-increase
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductIncreaseCommand {
    private String messageId;
    private Integer orderId;
    private Integer productId;
    private Integer quantity;
}
