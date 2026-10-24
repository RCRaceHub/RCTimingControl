package dev.monkeypatch.rctiming.query.championship;

import java.util.List;

/**
 * A championship's name and the classes it scores, so a standings sheet can head each class's table.
 *
 * @param classes the scored classes, in name order
 * @param splits  the category tags the standings are also split by, in display order (#494)
 */
public record ChampionshipInfoDto(long id, String name, List<ClassInfo> classes, List<String> splits) {

    /** A racing class the championship scores. */
    public record ClassInfo(long racingClassId, String name) {}
}
