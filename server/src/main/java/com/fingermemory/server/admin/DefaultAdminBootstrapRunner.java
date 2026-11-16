package com.fingermemory.server.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.admin.bootstrap.enabled", havingValue = "true")
public class DefaultAdminBootstrapRunner implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAdminBootstrapRunner.class);

    private final AdminBootstrapProperties properties;
    private final DefaultAdminBootstrapService bootstrapService;

    public DefaultAdminBootstrapRunner(
            AdminBootstrapProperties properties,
            DefaultAdminBootstrapService bootstrapService) {
        this.properties = properties;
        this.bootstrapService = bootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        DefaultAdminBootstrapService.BootstrapResult result =
                bootstrapService.bootstrap(properties);
        if (result.created()) {
            LOGGER.info("Created default administrator account: {}", result.normalizedUsername());
            return;
        }
        LOGGER.info(
                "Default administrator bootstrap already completed for account: {}",
                result.normalizedUsername());
    }
}
