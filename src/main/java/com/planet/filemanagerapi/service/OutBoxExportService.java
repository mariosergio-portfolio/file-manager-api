package com.planet.filemanagerapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planet.filemanagerapi.data.entity.OutboxExport;
import com.planet.filemanagerapi.data.entity.OutboxExportState;
import com.planet.filemanagerapi.data.repository.OutboxExportRepository;
import com.planet.filemanagerapi.data.repository.OutboxExportStateRepository;
import com.planet.filemanagerapi.domain.OutBoxState;
import com.planet.filemanagerapi.messaging.ExportEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutBoxExportService {

    private final OutboxExportRepository outboxExportRepository;
    private final OutboxExportStateRepository outboxImportStateRepository;
    private final ObjectMapper objectMapper;


    @Transactional
    public Long persistEvent(UUID exportId, OutBoxState outBoxState, ExportEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxExport outbox = OutboxExport.builder()
                    .exportId(exportId)
                    .payload(payload)
                    .state(outBoxState)
                    .build();
            outboxExportRepository.save(outbox);

            return outbox.getId();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event for importId: " + exportId, e);
        }
    }

    public void changeStatus(Long outBoxId, OutBoxState stateToChange) {
        try {

            OutboxExportState outbox = OutboxExportState.builder()
                    .id(outBoxId)
                    .state(stateToChange)
                    .build();
            outboxImportStateRepository.save(outbox);

        } catch (Exception e) {
            log.error("Failed to change outbox event to delete for outBoxId: " + outBoxId, e);
        }
    }
}
