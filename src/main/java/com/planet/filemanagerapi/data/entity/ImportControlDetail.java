package com.planet.filemanagerapi.data.entity;

import com.planet.filemanagerapi.domain.ImportControlDetailType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persistence log row for {@code import_control_detail}.
 * Each row records a single OK / ERROR / WARNING / SUCCESS event
 * that occurred during the import identified by {@code importControl}.
 */
@Entity
@Table(name = "import_control_detail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportControlDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "import_control_detail_id", nullable = false, updatable = false)
    private Long importControlDetailId;

    @Column(name = "import_id", nullable = false, updatable = false)
    private UUID importId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30)
    private ImportControlDetailType type;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;


    @Column(name = "description", length = 255)
    private String description;

}
