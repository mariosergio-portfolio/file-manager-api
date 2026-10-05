package com.planet.filemanagerapi.integration;

import com.planet.filemanagerapi.data.entity.ImportControl;
import com.planet.filemanagerapi.data.entity.ImportControlDetail;
import com.planet.filemanagerapi.data.entity.OutboxImport;
import com.planet.filemanagerapi.data.repository.ImportControlDetailRepository;
import com.planet.filemanagerapi.data.repository.ImportControlRepository;
import com.planet.filemanagerapi.data.repository.OutboxImportRepository;
import com.planet.filemanagerapi.domain.ImportControlDetailType;
import com.planet.filemanagerapi.domain.ImportState;
import com.planet.filemanagerapi.domain.OutBoxState;
import com.planet.filemanagerapi.dto.ImportResponse;
import com.planet.filemanagerapi.dto.ImportStatusResponse;
import com.planet.filemanagerapi.integration.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class FileImportFlowIT extends AbstractIntegrationTest {

    private static final AtomicLong COMPANY_ID_SEQUENCE = new AtomicLong(9_000_000L);

    @Autowired
    private ImportControlRepository importControlRepository;

    @Autowired
    private OutboxImportRepository outboxImportRepository;

    @Autowired
    private ImportControlDetailRepository importControlDetailRepository;

    @Value("${rabbitmq.routing-key.import}")
    private String importRoutingKey;

    @Test
    void importFile_HappyPath_PersistsPendingImportAndOutboxRow() {
        long companyId = nextCompanyId();

        ImportResponse importResponse = requestImport(companyId, "customer.csv", "CUSTOMER");

        UUID importId = importResponse.importId();
        assertThat(importResponse.state()).isEqualTo(ImportState.PENDING.name());

        ImportControl control = importControlRepository.findByCompanyIdAndImportId(companyId, importId).orElseThrow();
        assertThat(control.getState()).isEqualTo(ImportState.PENDING);
        assertThat(control.getOriginalFilename()).isEqualTo("customer.csv");
        assertThat(control.getFileReference()).isNotBlank();
        assertThat(control.getCompanyId()).isEqualTo(companyId);

        List<OutboxImport> outboxRows = outboxImportRepository.findAll().stream()
                .filter(o -> o.getImportId().equals(importId))
                .toList();
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.get(0).getState()).isEqualTo(OutBoxState.PENDING);
        assertThat(outboxRows.get(0).getRoutingKey()).isEqualTo(importRoutingKey + "." + companyId);

        ImportStatusResponse statusResponse = getImportStatus(companyId, importId);
        assertThat(statusResponse.state()).isEqualTo(ImportState.PENDING.name());
        assertThat(statusResponse.details()).isEmpty();
    }


    @Test
    void importFile_UnsupportedDomain_Returns422() {
        long companyId = nextCompanyId();

        String errorBody = client.post()
                .uri("/api/company/{companyId}/file-import", companyId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipartBody("product.csv", "PRODUCT"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(errorBody).contains("Domain not supported");
    }


    @Test
    void importStatus_HappyPath_ReturnsCompletedWithDetails_WhenWorkerHasFinishedProcessing() {
        long companyId = nextCompanyId();
        UUID importId = requestImport(companyId, "customer.csv", "CUSTOMER").importId();

        ImportControl control = importControlRepository.findByCompanyIdAndImportId(companyId, importId).orElseThrow();
        control.setState(ImportState.COMPLETED);
        importControlRepository.save(control);

        importControlDetailRepository.save(ImportControlDetail.builder()
                .importId(importId)
                .type(ImportControlDetailType.ERROR)
                .description("Row 4: missing required field 'email'")
                .build());
        importControlDetailRepository.save(ImportControlDetail.builder()
                .importId(importId)
                .type(ImportControlDetailType.WARNING)
                .description("Row 9: duplicate customer id, skipped")
                .build());

        ImportStatusResponse statusResponse = getImportStatus(companyId, importId);

        assertThat(statusResponse.state()).isEqualTo(ImportState.COMPLETED.name());
        assertThat(statusResponse.details()).hasSize(2)
                .extracting("type")
                .containsExactlyInAnyOrder(ImportControlDetailType.ERROR, ImportControlDetailType.WARNING);
    }

    @Test
    void importStatus_UnknownImportId_Returns400() {
        long companyId = nextCompanyId();

        client.get()
                .uri("/api/company/{companyId}/file-import/{importId}/status", companyId, UUID.randomUUID())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ImportResponse requestImport(long companyId, String filename, String domain) {
        return client.post()
                .uri("/api/company/{companyId}/file-import", companyId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipartBody(filename, domain))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.ACCEPTED)
                .expectBody(ImportResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private ImportStatusResponse getImportStatus(long companyId, UUID importId) {
        return client.get()
                .uri("/api/company/{companyId}/file-import/{importId}/status", companyId, importId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ImportStatusResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private MultiValueMap<String, Object> multipartBody(String filename, String domain) {
        ByteArrayResource fileResource = new ByteArrayResource(
                "id,name,email\n1,Ada Lovelace,ada@example.com\n".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        body.add("domain", domain);
        return body;
    }

    private long nextCompanyId() {
        return COMPANY_ID_SEQUENCE.incrementAndGet();
    }
}
