package com.knox.galaxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The bell feed plus its unread count — one call, because the badge and the
 * dropdown are always rendered together.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationFeedResponse {
    private long unreadCount;
    private List<NotificationResponse> notifications;
}
