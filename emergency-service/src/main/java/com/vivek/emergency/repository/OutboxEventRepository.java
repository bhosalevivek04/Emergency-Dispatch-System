package com.vivek.emergency.repository;

import com.vivek.emergency.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("SELECT o FROM OutboxEvent o WHERE o.published = false " +
           "AND o.retryCount < 5 " +
           "ORDER BY o.createdAt ASC")
    List<OutboxEvent> findUnpublishedEvents();

    @Query("SELECT COUNT(o) FROM OutboxEvent o WHERE o.published = false")
    long countUnpublished();

    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.published = true AND o.publishedAt < :threshold")
    int deleteOldPublishedEvents(@Param("threshold") LocalDateTime threshold);
}
