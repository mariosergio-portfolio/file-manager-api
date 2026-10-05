package com.planet.filemanagerapi.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

public interface StorageService {

    InputStream download(String fileReference);

    String upload(UUID importId, Long companyId, MultipartFile file);

}
