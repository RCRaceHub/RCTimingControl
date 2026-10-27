package dev.monkeypatch.rctiming.domain.event;

/**
 * Which finals of an event are run in legs ({@link Event#getFinalLegsScope()}). How many legs, and how many of a
 * driver's legs count, still come from the class's format; this only says whether the A final alone has them, as at
 * nationals, or every final of the class has them, scored on points over their legs. Read when the finals are
 * calculated, so changing it does not touch finals that already exist.
 */
public enum FinalLegsScope {
    A_ONLY("The A final only"),
    ALL("Every final");

    private final String label;

    FinalLegsScope(String label) {
        this.label = label;
    }

    /** What an official reads, such as "The A final only". */
    public String label() {
        return label;
    }

    /** Whether the final at {@code index} (0 for A, 1 for B and so on) is run in legs. */
    public boolean appliesTo(int index) {
        return this == ALL || index == 0;
    }
}
