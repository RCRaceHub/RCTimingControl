package dev.monkeypatch.rctiming.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rctiming.provisioning.*}: answers to the first-run setup wizard, given in the settings file or as
 * environment variables so a fresh install (a Docker container, a rebuilt laptop) needs no wizard. Read once at
 * start-up by {@link ProvisioningRunner}, and applied only while the database has no accounts; see
 * {@code docs/provisioning.md}.
 *
 * @param admin   the first admin. Setting {@code email} switches provisioning on
 * @param club    the club's details; leave {@code name} empty to do the club step in the wizard
 * @param decoder where the decoder is; leave {@code host} empty to set it later
 */
@ConfigurationProperties(prefix = "rctiming.provisioning")
public record ProvisioningProperties(
        @DefaultValue Admin admin,
        @DefaultValue Club club,
        @DefaultValue Decoder decoder) {

    /** The first admin; {@code password} is at least 8 characters, and best given as an environment variable. */
    public record Admin(String email, String password, String firstName, String lastName) {}

    /** The club details the wizard's club step saves. {@code timezone} is an IANA zone id such as {@code Europe/London}. */
    public record Club(String name, String email, String phone, String websiteUrl,
                       @DefaultValue("UTC") String timezone) {}

    /** The decoder address; {@code protocol} is {@code RC4} (the text protocol) or {@code P3}. */
    public record Decoder(String host, @DefaultValue("5100") Integer port, @DefaultValue("RC4") String protocol) {}

    /** Whether the settings ask for the first admin to be created. */
    public boolean enabled() {
        return admin.email() != null && !admin.email().isBlank();
    }
}
