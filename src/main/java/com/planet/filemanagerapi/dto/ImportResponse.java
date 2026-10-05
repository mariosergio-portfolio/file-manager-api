package com.planet.filemanagerapi.dto;

import java.util.UUID;

public record ImportResponse(UUID importId, String state) {}
