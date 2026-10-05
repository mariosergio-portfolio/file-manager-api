package com.planet.filemanagerapi.data.repository;

import com.planet.filemanagerapi.data.entity.ExportControl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExportControlRepository extends JpaRepository<ExportControl, Long> {

    Optional<ExportControl> findByCompanyIdAndExportId(Long companyId, UUID exportId);
}
