package dev.monkeypatch.rctiming.api.pub.dto;

import java.time.Instant;
import java.util.List;

/**
 * The About page's data. {@code version} and {@code buildTime} come from the build's properties;
 * {@code addresses}: the URLs other devices on the venue network can open (#23);
 * {@code stableAddress}: the mDNS URL such as {@code http://timing.local:8080/} once it is really announced, else
 * null. QR codes use it so printed codes survive an address change, and fall back to {@code addresses};
 * {@code httpsAddresses}: the HTTPS addresses, empty when HTTPS is off; {@code httpsEnabled}: whether the club
 * certificate authority can be downloaded from {@code /api/v1/about/ca.crt} (#503);
 * {@code commit}: the short git commit the build came from, or "unknown" when it was built outside a checkout. The
 * version alone cannot tell a build of main from a release, as {@code VERSION} only changes at a release.
 */
public record AboutDto(String version, Instant buildTime, List<String> addresses, String stableAddress,
                       List<String> httpsAddresses, boolean httpsEnabled, String commit) {}
