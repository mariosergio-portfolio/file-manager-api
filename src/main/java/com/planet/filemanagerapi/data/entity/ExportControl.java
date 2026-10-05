package com.planet.filemanagerapi.data.entity;

import com.planet.filemanagerapi.domain.ExportFormat;
import com.planet.filemanagerapi.domain.ExportState;
import com.planet.filemanagerapi.domain.FileDomain;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "EXPORT_CONTROL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportControl {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "export_control_id", nullable = false, updatable = false)
    private Long exportControlId;

    @Column(name = "export_id", nullable = false, updatable = false)
    private UUID exportId;

    @Column(name = "file_reference", nullable = false)
    private String fileReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private ExportState state;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain", nullable = false, length = 20)
    private FileDomain domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 10)
    private ExportFormat format;

    @Column(name = "columns", nullable = false, length = 1000)
    private String columns;

    @Column(name = "email", length = 255)
    private String customerEmail;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;


    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
