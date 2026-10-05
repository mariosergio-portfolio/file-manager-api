package com.planet.filemanagerapi.data.repository;

import com.planet.filemanagerapi.data.entity.ExportControl;
import com.planet.filemanagerapi.data.entity.ImportControl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImportControlRepository extends JpaRepository<ImportControl, Long> {

    Optional<ImportControl> findByCompanyIdAndImportId(Long companyId, UUID importId);
}
