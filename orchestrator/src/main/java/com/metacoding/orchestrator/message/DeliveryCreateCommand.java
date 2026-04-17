package com.metacoding.orchestrator.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryCreateCommand {
    private String messageId;
    private Integer orderId;
    private String address;
}
