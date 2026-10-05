package com.planet.filemanagerapi.dto;

import java.util.List;
import java.util.UUID;

public record ImportStatusResponse(UUID importId, String state, List<ImportControlDetailDto> details) {}
