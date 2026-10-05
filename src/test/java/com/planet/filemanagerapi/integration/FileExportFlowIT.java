package com.planet.filemanagerapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planet.filemanagerapi.data.entity.ExportControl;
import com.planet.filemanagerapi.data.entity.OutboxExport;
import com.planet.filemanagerapi.data.repository.ExportControlRepository;
import com.planet.filemanagerapi.data.repository.OutboxExportRepository;
import com.planet.filemanagerapi.domain.ExportFormat;
import com.planet.filemanagerapi.domain.ExportState;
import com.planet.filemanagerapi.domain.FileDomain;
import com.planet.filemanagerapi.domain.OutBoxState;
import com.planet.filemanagerapi.dto.ExportRequest;
import com.planet.filemanagerapi.dto.ExportResponse;
import com.planet.filemanagerapi.dto.ExportStatusResponse;
import com.planet.filemanagerapi.integration.support.AbstractIntegrationTest;
import com.planet.filemanagerapi.integration.support.TestExportRabbitTopologyConfig;
import com.planet.filemanagerapi.messaging.ExportEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Import(TestExportRabbitTopologyConfig.class)
class FileExportFlowIT extends AbstractIntegrationTest {

    private static final AtomicLong COMPANY_ID_SEQUENCE = new AtomicLong(8_000_000L);

    @Autowired
    private ExportControlRepository exportControlRepository;

    @Autowired
    private OutboxExportRepository outboxExportRepository;

    @Autowired
    @Qualifier("exportRabbitTemplate")
    private RabbitTemplate exportRabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${rabbitmq.queue.export}")
    private String exportQueueName;

    @Value("${rabbitmq.routing-key.export}")
    private String exportRoutingKey;

    @BeforeEach
    void drainExportQueue() {
        // other test methods in this class publish to the same real queue without consuming;
        // start each test with an empty queue so receive() can't pick up a stale message
        while (exportRabbitTemplate.receive(exportQueueName, 50) != null) {
            // keep draining
        }
    }

    @Test
    void requestExport_HappyPath_PublishesEventAndMarksOutboxDeleted() throws Exception {
        // Arrange: a unique companyId (test isolation) and a valid export request payload
        long companyId = nextCompanyId();
        ExportRequest request = new ExportRequest(ExportFormat.CSV, List.of("id", "name", "email"),
                FileDomain.CUSTOMER, "customer@example.com");

        // Act: call POST /file-export
        ExportResponse response = requestExport(companyId, request);

        // Assert: the HTTP response itself is 202 Accepted with state PENDING
        UUID exportId = response.exportId();
        assertThat(response.state()).isEqualTo(ExportState.PENDING.name());

        // Assert: ExportService persisted an ExportControl row matching the request
        ExportControl control = exportControlRepository.findByCompanyIdAndExportId(companyId, exportId).orElseThrow();
        assertThat(control.getState()).isEqualTo(ExportState.PENDING);
        assertThat(control.getFormat()).isEqualTo(ExportFormat.CSV);
        assertThat(control.getColumns()).isEqualTo("id,name,email");
        assertThat(control.getCustomerEmail()).isEqualTo("customer@example.com");

        // Assert: the event was actually published to the real RabbitMQ broker, on the
        // expected routing key (proves ExportEventPublisher.publish() worked end to end)
        Message message = exportRabbitTemplate.receive(exportQueueName, 5000);
        assertThat(message).as("export event should have been published to the export queue").isNotNull();
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo(exportRoutingKey);

        // Assert: the published message payload matches what was requested
        ExportEvent event = objectMapper.readValue(message.getBody(), ExportEvent.class);
        assertThat(event.exportId()).isEqualTo(exportId);
        assertThat(event.companyId()).isEqualTo(companyId);
        assertThat(event.domain()).isEqualTo(FileDomain.CUSTOMER);
        assertThat(event.format()).isEqualTo(ExportFormat.CSV);
        assertThat(event.columns()).containsExactly("id", "name", "email");

        // Assert (async): once the broker confirms receipt, ExportEventPublisher's confirm
        // callback flips the outbox row to DELETED - poll since the confirm arrives on a
        // separate I/O thread, not synchronously with the publish call
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<OutboxExport> outboxRows = outboxExportRepository.findAll().stream()
                    .filter(o -> o.getExportId().equals(exportId))
                    .toList();
            assertThat(outboxRows).hasSize(1);
            assertThat(outboxRows.get(0).getState()).isEqualTo(OutBoxState.DELETED);
        });
    }

    @Test
    void exportStatus_ReturnsPending_RightAfterCreation() {
        long companyId = nextCompanyId();
        UUID exportId = requestExport(companyId, defaultRequest()).exportId();

        ExportStatusResponse statusResponse = client.get()
                .uri("/api/company/{companyId}/file-export/{exportId}/status", companyId, exportId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ExportStatusResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(statusResponse.state()).isEqualTo(ExportState.PENDING.name());
    }

    @Test
    void downloadExport_NotYetCompleted_Returns422() {
        long companyId = nextCompanyId();
        UUID exportId = requestExport(companyId, defaultRequest()).exportId();

        client.get()
                .uri("/api/company/{companyId}/file-export/{exportId}/download", companyId, exportId)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void downloadExport_Completed_ReturnsFileContent() throws Exception {
        long companyId = nextCompanyId();
        UUID exportId = requestExport(companyId, defaultRequest()).exportId();

        String fileReference = companyId + "-" + exportId + ".csv";
        byte[] fileContent = "id,name,email\n1,Ada Lovelace,ada@example.com\n".getBytes(StandardCharsets.UTF_8);
        Path exportDir = STORAGE_DIR.resolve("export");
        Files.createDirectories(exportDir);
        Files.write(exportDir.resolve(fileReference), fileContent);

        ExportControl control = exportControlRepository.findByCompanyIdAndExportId(companyId, exportId).orElseThrow();
        control.setState(ExportState.COMPLETED);
        control.setFileReference(fileReference);
        exportControlRepository.save(control);

        EntityExchangeResult<byte[]> result = client.get()
                .uri("/api/company/{companyId}/file-export/{exportId}/download", companyId, exportId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class)
                .returnResult();

        assertThat(result.getResponseHeaders().getContentType()).isEqualTo(ExportFormat.CSV.contentType());
        assertThat(result.getResponseHeaders().getContentDisposition().getFilename()).isEqualTo("export_" + fileReference);
        assertThat(result.getResponseBody()).isEqualTo(fileContent);
    }

    @Test
    void exportStatus_UnknownExportId_Returns400() {
        long companyId = nextCompanyId();

        client.get()
                .uri("/api/company/{companyId}/file-export/{exportId}/status", companyId, UUID.randomUUID())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ExportRequest defaultRequest() {
        return new ExportRequest(ExportFormat.CSV, List.of("id", "name"), FileDomain.CUSTOMER, "someone@example.com");
    }

    private ExportResponse requestExport(long companyId, ExportRequest request) {
        return client.post()
                .uri("/api/company/{companyId}/file-export", companyId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.ACCEPTED)
                .expectBody(ExportResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private long nextCompanyId() {
        return COMPANY_ID_SEQUENCE.incrementAndGet();
    }
}
