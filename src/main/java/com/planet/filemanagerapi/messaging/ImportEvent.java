package com.planet.filemanagerapi.messaging;

import com.planet.filemanagerapi.domain.FileDomain;
import lombok.Builder;

import java.util.UUID;

@Builder
public record ImportEvent(
        UUID importId,
        String state,
        String fileReference,
        Long companyId,
        FileDomain domain
) {}
