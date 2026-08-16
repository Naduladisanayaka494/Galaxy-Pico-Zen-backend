package com.knox.galaxy.repository;

import com.knox.galaxy.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * The bell feed for one person: their own notifications plus every
     * system-wide one. {@code userId} is never null — the service resolves the
     * caller first — so this binds no null parameter.
     */
    @Query("SELECT n FROM Notification n "
            + "WHERE (n.user IS NULL OR n.user.id = :userId) "
            + "ORDER BY n.createdAt DESC")
    Page<Notification> feedFor(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT n FROM Notification n "
            + "WHERE (n.user IS NULL OR n.user.id = :userId) AND n.isRead = false "
            + "ORDER BY n.createdAt DESC")
    Page<Notification> unreadFeedFor(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n "
            + "WHERE (n.user IS NULL OR n.user.id = :userId) AND n.isRead = false")
    long countUnreadFor(@Param("userId") Long userId);

    /** Bulk "mark all read" — one statement instead of a read-modify-write loop. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true "
            + "WHERE n.isRead = false AND (n.user IS NULL OR n.user.id = :userId)")
    int markAllReadFor(@Param("userId") Long userId);
}
