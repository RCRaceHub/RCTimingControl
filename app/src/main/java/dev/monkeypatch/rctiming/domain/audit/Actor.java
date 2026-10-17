package dev.monkeypatch.rctiming.domain.audit;

/**
 * Who did something that is being recorded.
 *
 * @param userId the official, or null for the command line, a background job or someone not signed in
 * @param label  who that was as text, or null to have {@link AuditService} fill in the official's name
 * @param source where the action came from
 */
public record Actor(Long userId, String label, Source source) {

    /** Where an action came from: an official in the browser, the command line, or a background job. */
    public enum Source { UI, CLI, SYSTEM }

    /** An official acting through the app. The name is looked up when the row is written. */
    public static Actor official(long userId) {
        return new Actor(userId, null, Source.UI);
    }

    /** Someone who is not signed in, for example a failed sign-in. The label says what is known, never an email. */
    public static Actor anonymous(String label) {
        return new Actor(null, "anonymous:" + (label == null ? "" : label.strip()), Source.UI);
    }

    /** A command line tool run on the laptop, by the operating system user named. */
    public static Actor cli(String osUser) {
        return new Actor(null, "cli:" + osUser, Source.CLI);
    }

    /** A background job, with no person behind it. */
    public static Actor system(String job) {
        return new Actor(null, "system:" + job, Source.SYSTEM);
    }
}
