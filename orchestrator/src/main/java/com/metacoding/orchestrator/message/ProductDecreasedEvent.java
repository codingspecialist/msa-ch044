package com.metacoding.orchestrator.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDecreasedEvent {
    private Integer orderId;
    private Integer productId;
    private boolean success;
}
