package com.metacoding.product.repository;

import com.metacoding.product.domain.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, String> {
}
