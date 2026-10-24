package dev.monkeypatch.rctiming.domain.auth;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RefreshTokensRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a refresh token between entity and row; MapStruct generates the implementation, which
 * {@link RefreshTokenRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface RefreshTokenMapper {

    RefreshToken toEntity(RefreshTokensRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(RefreshToken t, @MappingTarget RefreshTokensRecord record);
}
