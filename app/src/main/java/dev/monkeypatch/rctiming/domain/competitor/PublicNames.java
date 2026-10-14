package dev.monkeypatch.rctiming.domain.competitor;

import org.springframework.stereotype.Component;

import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto.ResultRow;

import java.util.List;
import java.util.Map;

/**
 * The name to show the public for a competitor (#265): the full name, initials only, or a stand-in, as an official
 * has set it. Every public screen and the live feed go through here, so there is one rule. Officials' own screens
 * (race control, check-in, the competitor lists) keep the full name.
 *
 * <p>A {@link Snapshot} reads the restricted competitors once, for code that names many drivers.
 */
@Component
public class PublicNames {

    /** What a hidden competitor is called when there is no car number to use. */
    static final String HIDDEN = "Driver";

    private final CompetitorRepository competitors;

    public PublicNames(CompetitorRepository competitors) {
        this.competitors = competitors;
    }

    /** The competitors whose name is not shown in full, read once. */
    public Snapshot snapshot() {
        return snapshotFrom(competitors);
    }

    /** For services that already hold the competitor repository. */
    public static Snapshot snapshotFrom(CompetitorRepository competitors) {
        return new Snapshot(competitors.findNameVisibilityNotShow());
    }

    /** The public name of one competitor; {@code competitorId} may be null for an entry with none. */
    public String of(Long competitorId, String displayName, Object carNumber) {
        return snapshot().of(competitorId, displayName, carNumber);
    }

    /** Result rows with each name as the public may see it. */
    public List<ResultRow> rows(List<ResultRow> rows) {
        Snapshot snapshot = snapshot();
        return rows.stream()
                .map(r -> new ResultRow(r.position(), r.entryId(), r.competitorId(),
                        snapshot.of(r.competitorId(), r.driverName(), r.carNumber()), r.carNumber(),
                        r.lapsCompleted(), r.totalTimeMs(), r.bestLapMs(), r.gapToLeaderMs()))
                .toList();
    }

    public record Snapshot(Map<Long, NameVisibility> restricted) {

        public String of(Long competitorId, String displayName, Object carNumber) {
            NameVisibility visibility = competitorId == null ? null : restricted.get(competitorId);
            return PublicNames.format(visibility, displayName, carNumber);
        }
    }

    /** Applies {@code visibility} to a name; {@code null} or {@code SHOW} leaves it as it is. */
    static String format(NameVisibility visibility, String displayName, Object carNumber) {
        if (visibility == null || visibility == NameVisibility.SHOW || displayName == null) {
            return displayName;
        }
        return switch (visibility) {
            case INITIALS -> initials(displayName);
            case HIDE -> carNumber == null || carNumber.toString().isBlank()
                    ? HIDDEN : HIDDEN + " " + carNumber;
            case SHOW -> displayName;
        };
    }

    /** "Ada King-Lovelace" becomes "A. K."; words that start with a symbol, such as a bracketed nickname, are left out. */
    static String initials(String displayName) {
        StringBuilder out = new StringBuilder();
        for (String word : displayName.trim().split("\\s+")) {
            if (!word.isEmpty() && Character.isLetterOrDigit(word.codePointAt(0))) {
                if (!out.isEmpty()) {
                    out.append(' ');
                }
                out.appendCodePoint(Character.toUpperCase(word.codePointAt(0))).append('.');
            }
        }
        return out.isEmpty() ? HIDDEN : out.toString();
    }
}
