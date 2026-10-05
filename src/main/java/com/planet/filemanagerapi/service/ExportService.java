package com.planet.filemanagerapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planet.filemanagerapi.data.entity.ExportControl;
import com.planet.filemanagerapi.data.entity.OutboxExport;
import com.planet.filemanagerapi.data.repository.ExportControlRepository;
import com.planet.filemanagerapi.data.repository.OutboxExportRepository;
import com.planet.filemanagerapi.domain.ExportState;
import com.planet.filemanagerapi.domain.OutBoxState;
import com.planet.filemanagerapi.dto.ExportRequest;
import com.planet.filemanagerapi.dto.ExportResponse;
import com.planet.filemanagerapi.dto.ExportStatusResponse;
import com.planet.filemanagerapi.exception.ExportNotReadyException;
import com.planet.filemanagerapi.messaging.ExportEvent;
import com.planet.filemanagerapi.messaging.ExportEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final ExportControlRepository exportControlRepository;
    private final ExportEventPublisher exportEventPublisher;
    private final OutBoxExportService outBoxExportService;

    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ExportResponse requestExport(Long companyId, ExportRequest request) {
        UUID exportId = UUID.randomUUID();
        log.info("Starting export: exportId={}, companyId={}, domain={}, format={}", exportId, companyId, request.domain(), request.format());

        String columnsJoined = String.join(",", request.columns());

        ExportControl control = ExportControl.builder()
                .exportId(exportId)
                .state(ExportState.PENDING)
                .companyId(companyId)
                .domain(request.domain())
                .format(request.format())
                .columns(columnsJoined)
                .customerEmail(request.customerEmail())
                .build();
        exportControlRepository.save(control);

        ExportEvent event = new ExportEvent(
                exportId,
                ExportState.PENDING.name(),
                companyId,
                request.domain(),
                request.format(),
                request.columns(),
                request.customerEmail()
        );
        Long outBoxId = outBoxExportService.persistEvent(exportId, OutBoxState.PENDING, event);

        // Asynchronously publish the event to RabbitMQ
        exportEventPublisher.publish(event, outBoxId);

        log.info("Export accepted: exportId={}", exportId);
        return new ExportResponse(exportId, ExportState.PENDING.name());
    }

    @Transactional(readOnly = true)
    public ExportStatusResponse getStatus(Long companyId, UUID exportId) {
        ExportControl control = exportControlRepository.findByCompanyIdAndExportId(companyId,exportId)
                .filter(c -> c.getCompanyId().equals(companyId))
                .orElseThrow(() -> new IllegalArgumentException("Export not found: " + exportId));

        return new ExportStatusResponse(control.getExportId(), control.getState().name());
    }

    @Transactional(readOnly = true)
    public InputStream downloadReport(Long companyId, UUID exportId) {

        ExportControl control = this.getExportControl(companyId, exportId);

        if (control.getState() != ExportState.COMPLETED) {
            throw new ExportNotReadyException(exportId, control.getState());
        }

        return storageService.download(control.getFileReference());
        //return storageService.download(control.getFileReference());
    }

    @Transactional(readOnly = true)
    public ExportControl getExportControl(Long companyId, UUID exportId) {
        return exportControlRepository.findByCompanyIdAndExportId(companyId,exportId)
                .filter(c -> c.getCompanyId().equals(companyId))
                .orElseThrow(() -> new IllegalArgumentException("Export not found: " + exportId));
    }


}
