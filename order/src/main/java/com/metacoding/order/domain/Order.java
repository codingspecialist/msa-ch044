package com.metacoding.order.domain;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "order_tb")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer userId;
    private Integer productId;
    private Integer quantity;
    private String status; // PENDING / COMPLETED / CANCELLED
}
