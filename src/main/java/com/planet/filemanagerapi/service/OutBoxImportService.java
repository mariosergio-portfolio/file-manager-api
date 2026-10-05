package com.planet.filemanagerapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planet.filemanagerapi.data.entity.OutboxImport;
import com.planet.filemanagerapi.data.repository.OutboxImportRepository;
import com.planet.filemanagerapi.domain.OutBoxState;
import com.planet.filemanagerapi.messaging.ImportEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutBoxImportService {

    private final OutboxImportRepository outboxImportRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Long persistEvent(UUID importId, OutBoxState outBoxState, ImportEvent event, String routingKey) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxImport outbox = OutboxImport.builder()
                    .importId(importId)
                    .routingKey(routingKey)
                    .payload(payload)
                    .state(outBoxState)
                    .build();
            outboxImportRepository.save(outbox);

            return outbox.getId();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event for importId: " + importId, e);
        }

    }

}
