package com.metacoding.delivery.repository;

import com.metacoding.delivery.domain.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryRepository extends JpaRepository<Delivery, Integer> {
    List<Delivery> findAllByOrderByIdDesc();
}
