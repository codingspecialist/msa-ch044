package com.metacoding.order.repository;

import com.metacoding.order.domain.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, String> {
}
