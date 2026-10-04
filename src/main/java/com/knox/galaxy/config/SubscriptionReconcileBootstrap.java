package com.knox.galaxy.config;

import com.knox.galaxy.service.ClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Heals {@code knox.subscriptions} rows that drifted from the plan staff set in
 * the client manager (Galaxy-client) — e.g. tenants created before the
 * client → subscription sync existed, which were all provisioned as Basic.
 * Idempotent, so it is safe on every boot.
 */
@Configuration
public class SubscriptionReconcileBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionReconcileBootstrap.class);

    @Bean
    public ApplicationRunner reconcileSubscriptions(ClientService clientService) {
        return args -> {
            try {
                clientService.reconcileAllSubscriptions();
                log.info("Reconciled tenant subscriptions with client-manager plans");
            } catch (RuntimeException e) {
                log.error("Subscription reconcile failed; continuing startup", e);
            }
        };
    }
}
