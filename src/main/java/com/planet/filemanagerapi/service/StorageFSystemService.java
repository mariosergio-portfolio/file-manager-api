package com.planet.filemanagerapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Profile("file-system")
@Slf4j
public class StorageFSystemService implements StorageService {

    final String FILE_EXTENSION = ".CSV";

    @Value("${storage.local.import.base-path}")
    private String basePathImport;

    @Value("${storage.local.export.base-path}")
    private String basePathExport;

    @Override
    public String upload(UUID importId, Long companyId, MultipartFile file) {
        String fileReference = companyId + "-" + importId.toString() + FILE_EXTENSION;
        Path filePath = Paths.get(basePathImport, fileReference);
        log.info("Uploading file to filesystem: path={}, size={}", filePath, file.getSize());
        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, file.getBytes());
            log.info("File uploaded to filesystem: fileReference={}", fileReference);
            return fileReference;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file to filesystem for fileId: " + importId, e);
        }
    }

    @Override
    public InputStream download(String fileReference) {
        Path filePath = Paths.get(basePathExport, fileReference);
        log.info("Downloading file from filesystem: path={}", filePath);
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            log.info("File downloaded from filesystem: fileReference={}", fileReference);

            return new ByteArrayInputStream(bytes);

        } catch (IOException e) {
            throw new RuntimeException("Failed to read file from filesystem: " + filePath, e);
        }
    }
}
