package dev.monkeypatch.rctiming.domain.club;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ClubProfilesRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Generated at compile time by MapStruct; {@link ClubProfileRepository} calls it to move a club
 * profile between its entity and its row. A column added to the table without a matching entity
 * property, or the reverse, fails the build.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface ClubProfileMapper {

    @Mapping(target = "liveTimingSettings", source = "record")
    ClubProfile toEntity(ClubProfilesRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "battleGapMs", source = "liveTimingSettings.battleGapMs")
    @Mapping(target = "battleTrendMs", source = "liveTimingSettings.battleTrendMsPerLap")
    @Mapping(target = "battleFastMs", source = "liveTimingSettings.battleFastMsPerLap")
    @Mapping(target = "battleHoldLaps", source = "liveTimingSettings.battleHoldLaps")
    @Mapping(target = "closeGapMs", source = "liveTimingSettings.closeGapMs")
    @Mapping(target = "lateMarginMs", source = "liveTimingSettings.lateMarginMs")
    @Mapping(target = "hiddenRowIcons", expression = "java(rowIcons(profile.getLiveTimingSettings().hiddenRowIcons()))")
    void toRecord(ClubProfile profile, @MappingTarget ClubProfilesRecord record);

    default LiveTimingSettings liveTimingSettings(ClubProfilesRecord r) {
        return new LiveTimingSettings(r.getBattleGapMs(), r.getBattleTrendMs(), r.getBattleFastMs(),
                r.getBattleHoldLaps(), r.getCloseGapMs(), r.getLateMarginMs(), rowIcons(r.getHiddenRowIcons()));
    }

    default String rowIcons(List<String> icons) {
        return String.join(",", icons);
    }

    default List<String> rowIcons(String csv) {
        return csv == null || csv.isBlank() ? List.of() : List.of(csv.split(","));
    }

    default ClubAudioSettings audioSettings(String json) {
        return ClubProfileRepository.AUDIO_SETTINGS.convertToEntityAttribute(json);
    }

    default String audioSettings(ClubAudioSettings settings) {
        return ClubProfileRepository.AUDIO_SETTINGS.convertToDatabaseColumn(settings);
    }
}
