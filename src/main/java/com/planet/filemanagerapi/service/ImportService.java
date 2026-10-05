package com.planet.filemanagerapi.service;

import com.planet.filemanagerapi.domain.FileDomain;

import com.planet.filemanagerapi.data.entity.ImportControl;
import com.planet.filemanagerapi.domain.ImportState;
import com.planet.filemanagerapi.domain.OutBoxState;
import com.planet.filemanagerapi.dto.ImportControlDetailDto;
import com.planet.filemanagerapi.dto.ImportResponse;
import com.planet.filemanagerapi.dto.ImportStatusResponse;
import com.planet.filemanagerapi.exception.UnsupportedDomainException;
import com.planet.filemanagerapi.data.repository.ImportControlDetailRepository;
import com.planet.filemanagerapi.data.repository.ImportControlRepository;
import com.planet.filemanagerapi.messaging.ImportEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportService {

    private final StorageService storageService;
    private final ImportControlRepository importControlRepository;
    private final ImportControlDetailRepository importControlDetailRepository;
    private final OutBoxImportService outBoxService;

    @Value("${rabbitmq.routing-key.import}")
    private String importRoutingKey;

    @Transactional
    public ImportResponse importFile(Long companyId, MultipartFile file, FileDomain domain) {
        if (domain != FileDomain.CUSTOMER) {
            throw new UnsupportedDomainException("Domain not supported yet, it will be supported in the future: " + domain);
        }

        UUID importId = UUID.randomUUID();
        log.info("Starting import: importId={}, companyId={}, domain={}, filename={}", importId, companyId, domain, file.getOriginalFilename());

        String fileReference = storageService.upload(importId, companyId, file);

        ImportControl control = ImportControl.builder()
                .importId(importId)
                .originalFilename(file.getOriginalFilename())
                .fileReference(fileReference)
                .state(ImportState.PENDING)
                .companyId(companyId)
                .domain(domain)
                .build();
        importControlRepository.save(control);


        ImportEvent event = ImportEvent.builder()
                .importId(importId)
                .state(ImportState.PENDING.name())
                .fileReference(fileReference)
                .companyId(companyId)
                .domain(domain)
                .build();

        String routingKey = importRoutingKey + "." + companyId;
        outBoxService.persistEvent(importId, OutBoxState.PENDING, event, routingKey);

        log.info("Import accepted: importId={}", importId);
        return new ImportResponse(importId, ImportState.PENDING.name());
    }

    @Transactional(readOnly = true)
    public ImportStatusResponse getStatus(Long companyId, UUID importId) {
        ImportControl control = importControlRepository.findByCompanyIdAndImportId(companyId,importId)
                .filter(c -> c.getCompanyId().equals(companyId))
                .orElseThrow(() -> new IllegalArgumentException("Import not found: " + importId));

        List<ImportControlDetailDto> details = importControlDetailRepository.findByImportId(importId)
                .stream()
                .map(d -> new ImportControlDetailDto(d.getType(), d.getDescription()))
                .collect(Collectors.toList());

        return new ImportStatusResponse(control.getImportId(), control.getState().name(), details);
    }

}
