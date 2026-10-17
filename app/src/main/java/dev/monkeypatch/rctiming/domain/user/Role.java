package dev.monkeypatch.rctiming.domain.user;

/**
 * The staff roles, which stack: one account can hold any combination. {@link #ADMIN} can do everything the others can
 * plus the club's configuration, {@link #RACE_DIRECTOR} runs the day, and {@link #REFEREE} raises incidents and applies
 * penalties. Competitors have no role because they have no account.
 */
public enum Role {
    ADMIN, RACE_DIRECTOR, REFEREE;

    /** Only officials can sign in (L10, #18). */
    public static final java.util.Set<Role> OFFICIAL_ROLES = java.util.EnumSet.of(ADMIN, RACE_DIRECTOR, REFEREE);
}
