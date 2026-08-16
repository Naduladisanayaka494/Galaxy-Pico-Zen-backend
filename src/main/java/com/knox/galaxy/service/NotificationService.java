package com.knox.galaxy.service;

import com.knox.galaxy.dto.NotificationFeedResponse;
import com.knox.galaxy.dto.NotificationResponse;
import com.knox.galaxy.model.*;
import com.knox.galaxy.repository.BusinessSettingsRepository;
import com.knox.galaxy.repository.NotificationRepository;
import com.knox.galaxy.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The alert bell feed, and the one place notifications are raised from.
 *
 * <p>Whether an event produces a notification is controlled by the tenant's own
 * toggles in {@code business_settings} — {@code alert_new_order} and friends.
 * {@link #raise} checks the matching toggle, so callers just report what
 * happened and don't each re-implement the preference.
 *
 * <p>Raising is deliberately best-effort: a failure here is logged and
 * swallowed. Losing a bell entry must never roll back the order or user that
 * caused it.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private BusinessSettingsRepository businessSettingsRepository;
    @Autowired private UserRepository userRepository;

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public NotificationFeedResponse feed(String username, boolean unreadOnly, int page, int size) {
        User me = requireUser(username);
        PageRequest pageable = PageRequest.of(page, size);
        Page<Notification> found = unreadOnly
                ? notificationRepository.unreadFeedFor(me.getId(), pageable)
                : notificationRepository.feedFor(me.getId(), pageable);

        return new NotificationFeedResponse(
                notificationRepository.countUnreadFor(me.getId()),
                found.getContent().stream().map(this::toResponse).collect(Collectors.toList()));
    }

    // ----------------------------------------------------------------- writes

    @Transactional
    public NotificationResponse markRead(Long id, String username) {
        User me = requireUser(username);
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Notification " + id + " not found"));

        // A targeted notification belongs to one person; nobody else may touch it.
        if (notification.getUser() != null && !notification.getUser().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "That notification belongs to someone else");
        }
        notification.setRead(true);
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public long markAllRead(String username) {
        return notificationRepository.markAllReadFor(requireUser(username).getId());
    }

    // ------------------------------------------------------------------ raise

    /**
     * Records an event in the bell feed if the tenant has that alert switched on.
     *
     * @param recipient the person it concerns, or null for a system-wide alert
     */
    @Transactional
    public void raise(NotificationType type, String title, String body,
                      Order order, Product product, User recipient) {
        try {
            if (!isEnabled(type)) {
                return;
            }
            Notification notification = new Notification();
            notification.setType(type);
            notification.setTitle(title);
            notification.setBody(body);
            notification.setOrder(order);
            notification.setProduct(product);
            notification.setUser(recipient);
            notificationRepository.save(notification);
        } catch (RuntimeException e) {
            // Never let a missing bell entry take down the operation behind it.
            log.warn("Could not raise {} notification: {}", type, e.getMessage());
        }
    }

    /**
     * Maps a notification type onto the tenant's toggle. Types with no toggle
     * of their own (transfers, deliveries) are always on — the settings screen
     * offers no switch for them.
     */
    private boolean isEnabled(NotificationType type) {
        BusinessSettings settings = businessSettingsRepository
                .findById(BusinessSettings.SINGLETON_ID).orElse(null);
        if (settings == null) {
            return true;
        }
        Predicate<BusinessSettings> toggle;
        switch (type) {
            case new_order:           toggle = BusinessSettings::isAlertNewOrder; break;
            case order_status_update: toggle = BusinessSettings::isAlertStatusUpdate; break;
            case user_added:          toggle = BusinessSettings::isAlertUserAdded; break;
            case product_added:       toggle = BusinessSettings::isAlertProductAdded; break;
            default:                  return true;
        }
        return toggle.test(settings);
    }

    // ---------------------------------------------------------------- helpers

    private User requireUser(String username) {
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "No profile for " + username));
    }

    private NotificationResponse toResponse(Notification n) {
        Order order = n.getOrder();
        return new NotificationResponse(
                n.getId(), n.getType(), n.getTitle(), n.getBody(), n.isRead(),
                order == null ? null : order.getId(),
                order == null ? null : order.getOrderCode(),
                n.getProduct() == null ? null : n.getProduct().getId(),
                n.getCreatedAt());
    }

    /** Convenience for the common "system-wide, no linked entity" case. */
    @Transactional
    public void raise(NotificationType type, String title, String body) {
        raise(type, title, body, null, null, null);
    }

    /** Reads the tenant's configured low-stock threshold, defaulting to 5. */
    @Transactional(readOnly = true)
    public int lowStockThreshold() {
        return businessSettingsRepository.findById(BusinessSettings.SINGLETON_ID)
                .map(BusinessSettings::getLowStockThreshold)
                .orElse(5);
    }

    /** All notifications, for admin tooling. Not exposed on the tenant API. */
    @Transactional(readOnly = true)
    public List<NotificationResponse> all() {
        return notificationRepository.findAll().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }
}
