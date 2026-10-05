package com.planet.filemanagerapi.data.repository;

import com.planet.filemanagerapi.data.entity.ImportControlDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImportControlDetailRepository extends JpaRepository<ImportControlDetail, Long> {
    List<ImportControlDetail> findByImportId(UUID importId);
}
