package com.schoolerp.shared.storage;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    Optional<StoredFile> findByFileUuidAndDeletedAtIsNull(String fileUuid);

    @Query("SELECT COALESCE(SUM(f.sizeBytes), 0) FROM StoredFile f WHERE f.deletedAt IS NULL")
    long sumActiveSizeBytes();

    @Query("SELECT f.category AS category, COUNT(f) AS fileCount FROM StoredFile f WHERE f.deletedAt IS NULL GROUP BY f.category")
    List<CategoryCountProjection> countActiveFilesByCategory();

    interface CategoryCountProjection {
        FileCategory getCategory();

        long getFileCount();
    }
}

