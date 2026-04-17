package com.metacoding.delivery.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Command: delivery-create-command
// messageId = saga-{orderId}-create-delivery
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryCreateCommand {
    private String messageId;
    private Integer orderId;
    private String address;
}
