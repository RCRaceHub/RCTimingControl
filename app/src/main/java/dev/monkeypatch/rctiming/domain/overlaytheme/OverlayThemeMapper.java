package dev.monkeypatch.rctiming.domain.overlaytheme;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.OverlayThemesRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps an overlay theme between entity and row; MapStruct generates the implementation, which {@link OverlayThemeRepository} uses. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface OverlayThemeMapper {

    @Mapping(target = "default", source = "isDefault")
    OverlayTheme toEntity(OverlayThemesRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isDefault", source = "default")
    void toRecord(OverlayTheme theme, @MappingTarget OverlayThemesRecord record);
}
