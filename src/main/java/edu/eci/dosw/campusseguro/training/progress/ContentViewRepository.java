package edu.eci.dosw.campusseguro.training.progress;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ContentViewRepository extends JpaRepository<ContentView, UUID> {

    Optional<ContentView> findByUserIdAndContentId(UUID userId, UUID contentId);

    long countByUserIdAndContentId(UUID userId, UUID contentId);

    // Atomic in the database: two simultaneous views of the same user never lose an update.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ContentView v set v.lastViewedAt = :viewedAt "
            + "where v.userId = :userId and v.content.id = :contentId")
    int touch(@Param("userId") UUID userId, @Param("contentId") UUID contentId, @Param("viewedAt") Instant viewedAt);
}
