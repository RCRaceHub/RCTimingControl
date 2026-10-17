package dev.monkeypatch.rctiming.query.audit;

/** How the audit log's actor labels read when shown to an official. */
public final class AuditActors {

    private AuditActors() {
    }

    /**
     * The audit label holds the official's email and the system jobs' prefix; screens show the name only. A trailing
     * {@code <email>} is stripped, a {@code system:job} label reads {@code System (job)}, and null stays null.
     */
    public static String readable(String actorLabel) {
        if (actorLabel == null) {
            return null;
        }
        if (actorLabel.startsWith("system:")) {
            return "System (" + actorLabel.substring("system:".length()) + ")";
        }
        return actorLabel.replaceFirst("\\s*<[^>]*>$", "");
    }
}
