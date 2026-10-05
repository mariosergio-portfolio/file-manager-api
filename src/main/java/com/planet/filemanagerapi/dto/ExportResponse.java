package com.planet.filemanagerapi.dto;

import java.util.UUID;

public record ExportResponse(UUID exportId, String state) {}
