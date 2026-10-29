package dev.monkeypatch.rctiming.domain.auth;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.PasskeysRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps a passkey between entity and row; MapStruct generates the implementation, which {@link PasskeyRepository} uses. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface PasskeyMapper {

    Passkey toEntity(PasskeysRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(Passkey passkey, @MappingTarget PasskeysRecord record);
}
