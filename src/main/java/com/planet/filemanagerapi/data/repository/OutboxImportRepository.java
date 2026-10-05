package com.planet.filemanagerapi.data.repository;

import com.planet.filemanagerapi.data.entity.OutboxImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxImportRepository extends JpaRepository<OutboxImport, Long> {

/*    @Query("SELECT o FROM OutboxImport o WHERE o.state = :state AND o.createdAt < :before")
    List<OutboxImport> findStaleByState(@Param("state") ImportState state, @Param("before") LocalDateTime before);*/
}
