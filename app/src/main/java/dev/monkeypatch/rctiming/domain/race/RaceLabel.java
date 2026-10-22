package dev.monkeypatch.rctiming.domain.race;

/**
 * How a race is named to people: on the boards, the grid call, result snapshots and the round preview, so
 * they all use the same wording.
 */
public final class RaceLabel {

    private RaceLabel() {
    }

    /** "Qualifying 2 — Stock Buggy — Heat 1", or "A Final — Stock Buggy" for a final ("... — Leg 2" for a later leg). */
    public static String of(String roundType, int roundNumber, String className, int heatNumber, String finalLetter) {
        String round = round(roundType, roundNumber, finalLetter) + " — " + className;
        if ("FINAL".equals(roundType)) {
            // Leg 1 of a final in legs is the plain final; later legs are numbered
            return finalLetter != null && heatNumber > 1 ? round + " — Leg " + heatNumber : round;
        }
        return round + " — Heat " + heatNumber;
    }

    /** "Practice 1", "Qualifying 2" or "B Final"; a final without a letter is the A final, and letter Z is the Shikabane zombie round. */
    public static String round(String roundType, int roundNumber, String finalLetter) {
        return switch (roundType) {
            case "PRACTICE" -> "Practice " + roundNumber;
            case "QUALIFIER" -> "Qualifying " + roundNumber;
            case "FINAL" -> "Z".equals(finalLetter) ? "Zombie round" : (finalLetter != null ? finalLetter : "A") + " Final";
            default -> roundType + " " + roundNumber;
        };
    }
}
