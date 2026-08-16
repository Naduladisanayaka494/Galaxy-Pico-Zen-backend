package com.knox.galaxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.knox.galaxy.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One row of the alert bell feed. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private NotificationType type;
    private String title;
    private String body;

    /** Explicit name: Lombok would otherwise publish this as "read". */
    @JsonProperty("isRead")
    private boolean isRead;

    /** Optional jump targets for the "›" navigation. */
    private Long orderId;
    private String orderCode;
    private Long productId;

    private LocalDateTime createdAt;
}
