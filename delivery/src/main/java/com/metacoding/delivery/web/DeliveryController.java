package com.metacoding.delivery.web;

import com.metacoding.delivery.domain.Delivery;
import com.metacoding.delivery.message.DeliveryCompletedEvent;
import com.metacoding.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryRepository deliveryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // 관리자용: 배송 목록
    @GetMapping("/api/deliveries")
    public List<Delivery> list() {
        return deliveryRepository.findAllByOrderByIdDesc();
    }

    // 관리자용: 배송 완료 처리 → delivery-completed-event 발행
    @PutMapping("/api/deliveries/{id}/complete")
    public Delivery complete(@PathVariable Integer id) {
        Delivery delivery = deliveryRepository.findById(id).orElseThrow();
        delivery.setStatus("COMPLETED");
        deliveryRepository.save(delivery);

        kafkaTemplate.send("delivery-completed-event",
                String.valueOf(delivery.getOrderId()),
                new DeliveryCompletedEvent(delivery.getOrderId(), delivery.getId()));
        return delivery;
    }
}
