package com.metacoding.product.domain;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "product_tb")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String productName;
    private Integer quantity;
    private Long price;
}
