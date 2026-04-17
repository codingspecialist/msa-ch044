package com.metacoding.product.web;

import com.metacoding.product.domain.Product;
import com.metacoding.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;

    @GetMapping("/api/products")
    public List<Product> list() {
        return productRepository.findAll();
    }
}
