package com.metacoding.delivery.repository;

import com.metacoding.delivery.domain.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, String> {
}
