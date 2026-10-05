package com.planet.filemanagerapi.data.entity;

import com.planet.filemanagerapi.domain.FileDomain;
import com.planet.filemanagerapi.domain.ImportState;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "import_control")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportControl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "import_control_id", nullable = false, updatable = false)
    private Long importControlId;

    @Column(name = "import_id", nullable = false, updatable = false)
    private UUID importId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "file_reference", nullable = false)
    private String fileReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private ImportState state;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain", nullable = false, length = 20)
    private FileDomain domain;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;


    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
