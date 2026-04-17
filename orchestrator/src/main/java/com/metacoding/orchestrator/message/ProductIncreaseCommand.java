package com.metacoding.orchestrator.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductIncreaseCommand {
    private String messageId;
    private Integer orderId;
    private Integer productId;
    private Integer quantity;
}
