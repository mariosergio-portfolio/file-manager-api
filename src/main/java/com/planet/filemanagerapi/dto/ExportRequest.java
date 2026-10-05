package com.planet.filemanagerapi.dto;

import com.planet.filemanagerapi.domain.ExportFormat;
import com.planet.filemanagerapi.domain.FileDomain;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ExportRequest(
        @NotNull(message = "format is required") ExportFormat format,
        @NotEmpty(message = "columns must not be empty") List<String> columns,
        @NotNull(message = "domain is required") FileDomain domain,
        String customerEmail
) {}
