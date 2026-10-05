package com.planet.filemanagerapi.controller;

import com.planet.filemanagerapi.data.entity.ExportControl;
import com.planet.filemanagerapi.domain.ExportFormat;
import com.planet.filemanagerapi.dto.ExportRequest;
import com.planet.filemanagerapi.dto.ExportResponse;
import com.planet.filemanagerapi.dto.ExportStatusResponse;
import com.planet.filemanagerapi.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.planet.filemanagerapi.config.GlobalExceptionHandler.ErrorResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Tag(name = "File Export", description = "Endpoints for requesting, monitoring, and downloading file exports")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
@Slf4j
public class FileExportController {

    private final ExportService exportService;

    @Operation(summary = "Request an async file export")
    @PostMapping(value = "/company/{companyId}/file-export", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ExportResponse> requestExport(
            @PathVariable("companyId") @NotNull Long companyId,
            @Valid @RequestBody ExportRequest request) {

        log.info("Received export request: companyId={}, domain={}, format={}", companyId, request.domain(), request.format());
        ExportResponse response = exportService.requestExport(companyId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }


    @Operation(summary = "Get the status of an export")
    @GetMapping("/company/{companyId}/file-export/{exportId}/status")
    public ResponseEntity<?> getExportStatus(
            @PathVariable("companyId") @NotNull Long companyId,
            @PathVariable("exportId") @NotNull UUID exportId) {

        log.info("Export status request: companyId={}, exportId={}", companyId, exportId);
        ExportStatusResponse response = exportService.getStatus(companyId, exportId);
        return ResponseEntity.ok(response);
    }


    @Operation(summary = "Download the rendered export file (only when COMPLETED)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File downloaded successfully"),
            @ApiResponse(responseCode = "400", description = "Export not found for given companyId/exportId",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Export exists but is not yet COMPLETED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping(value = "/company/{companyId}/file-export/{exportId}/download",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> downloadExport(
            @PathVariable("companyId") @NotNull Long companyId,
            @PathVariable("exportId") @NotNull UUID exportId) throws IOException {

        log.info("Export download request: companyId={}, exportId={}", companyId, exportId);

        ExportControl control = exportService.getExportControl(companyId, exportId);
        InputStream inputStream = exportService.downloadReport(companyId, exportId);

        HttpHeaders headers = buildDownloadHeaders(control, exportId);

        StreamingResponseBody body = out -> {
            try (inputStream) {
                inputStream.transferTo(out);
            }
        };

        return ResponseEntity.ok().headers(headers).body(body);
    }


    private HttpHeaders buildDownloadHeaders(ExportControl control, UUID exportId) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(control.getFormat().contentType());
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename("export_" + control.getFileReference())
                        .build());
        return headers;
    }

}
