package com.planet.filemanagerapi.dto;

import java.util.UUID;

public record ExportStatusResponse(UUID exportId, String state) {}
