package com.planet.filemanagerapi.messaging;

import com.planet.filemanagerapi.domain.ExportFormat;
import com.planet.filemanagerapi.domain.FileDomain;

import java.util.List;
import java.util.UUID;

public record ExportEvent(
        UUID exportId,
        String state,
        Long companyId,
        FileDomain domain,
        ExportFormat format,
        List<String> columns,
        String customerEmail
) {}
