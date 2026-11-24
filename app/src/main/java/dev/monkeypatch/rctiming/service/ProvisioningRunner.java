package dev.monkeypatch.rctiming.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runs the first-start provisioning (see {@link ProvisioningProperties}) once the app has started. When the
 * settings are wrong it lets the exception stop start-up, because a container that silently skipped its admin would
 * leave the venue with a wizard anyone on the network could finish. The admin's password is never logged.
 */
@Component
@EnableConfigurationProperties(ProvisioningProperties.class)
public class ProvisioningRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningRunner.class);

    private final ProvisioningProperties properties;
    private final ProvisioningService provisioningService;

    public ProvisioningRunner(ProvisioningProperties properties, ProvisioningService provisioningService) {
        this.properties = properties;
        this.provisioningService = provisioningService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        if (provisioningService.apply(properties)) {
            log.info("Created the first admin and the club details from the provisioning settings");
        } else {
            log.info("Ignoring the provisioning settings: this database already has accounts");
        }
    }
}
