package com.knox.galaxy.repository;

import com.knox.galaxy.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /** Carousel order is the slot number, 1–5. */
    List<Announcement> findAllByIsActiveOrderByPositionAsc(boolean isActive);

    List<Announcement> findAllByOrderByPositionAsc();
}
