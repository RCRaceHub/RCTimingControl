package dev.monkeypatch.rctiming.domain.user;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.UsersRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a user between entity and row; MapStruct generates the implementation, which
 * {@link UserRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface UserMapper {

    @Mapping(target = "roles", ignore = true)
    User toEntity(UsersRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(User u, @MappingTarget UsersRecord record);
}
