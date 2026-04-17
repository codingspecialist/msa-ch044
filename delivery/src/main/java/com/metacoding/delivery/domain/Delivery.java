package com.metacoding.delivery.domain;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "delivery_tb")
public class Delivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer orderId;
    private String address;
    private String status; // READY / COMPLETED
}
