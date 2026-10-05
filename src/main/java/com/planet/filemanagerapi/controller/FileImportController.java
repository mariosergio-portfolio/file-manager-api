package com.planet.filemanagerapi.controller;

import com.planet.filemanagerapi.config.GlobalExceptionHandler;
import com.planet.filemanagerapi.domain.FileDomain;
import com.planet.filemanagerapi.dto.ImportResponse;
import com.planet.filemanagerapi.dto.ImportStatusResponse;
import com.planet.filemanagerapi.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "File Import", description = "Endpoints for uploading and importing CSV files")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
@Slf4j
public class FileImportController {

    private final ImportService importService;

    @Operation(summary = "Import a CSV file",
            requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)))
    @PostMapping(value = "/company/{companyId}/file-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "General bad request error",
                    content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Invalid Domain or file format",
                    content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)))
    })
    public ResponseEntity<ImportResponse> importFile(
            @PathVariable("companyId") @NotNull Long companyId,
            @RequestParam("file") @NotNull MultipartFile file,
            @RequestParam("domain") @NotNull FileDomain domain) {

        log.info("Received file import request: companyId={}, filename={}, domain={}", companyId, file.getOriginalFilename(), domain);
        ImportResponse response = importService.importFile(companyId, file, domain);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Operation(summary = "Get the status of an import")
    @GetMapping("/company/{companyId}/file-import/{importId}/status")
    public ResponseEntity<ImportStatusResponse> getImportStatus(
            @PathVariable("companyId") @NotNull Long companyId,
            @PathVariable("importId") @NotNull UUID importId) {

        log.info("Import status request: companyId={}, importId={}", companyId, importId);
        ImportStatusResponse response = importService.getStatus(companyId, importId);
        return ResponseEntity.ok(response);
    }
}
