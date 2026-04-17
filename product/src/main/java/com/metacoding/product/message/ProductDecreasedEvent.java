package com.metacoding.product.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Event: product-decreased-event
// 성공/실패 모두 동일 토픽 → success 플래그로 구분
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDecreasedEvent {
    private Integer orderId;
    private Integer productId;
    private boolean success;
}
