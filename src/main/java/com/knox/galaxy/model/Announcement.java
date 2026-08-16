package com.knox.galaxy.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

/**
 * A slide in the dashboard announcement carousel (§4).
 *
 * <p>{@code position} is 1–5 and unique — the slot the image occupies, not a
 * sort key that can repeat.
 */
@Entity
@Table(name = "announcements")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(nullable = false)
    private Short position;

    @JsonProperty("isActive")
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
