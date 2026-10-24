package dev.monkeypatch.rctiming.domain.user;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.UsersRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static dev.monkeypatch.rctiming.jooq.generated.tables.UserRoles.USER_ROLES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Users.USERS;

/** Officials, with their roles from {@code user_roles}. */
@Repository
public class UserRepository extends JooqRepository<User, UsersRecord> {

    private static final UserMapper MAPPER = Mappers.getMapper(UserMapper.class);

    public UserRepository(DSLContext dsl) {
        super(dsl, USERS, USERS.ID);
    }

    @Override
    protected String entityName() {
        return "Official";
    }

    public Optional<User> findByEmail(String email) {
        return findOne(USERS.EMAIL.eq(email));
    }

    /** Officials holding a role who can still sign in, such as the enabled admins (#61). */
    public long countEnabledWithRole(Role role) {
        return dsl.fetchCount(dsl.selectFrom(USERS)
                .where(USERS.DISABLED_AT.isNull())
                .andExists(dsl.selectOne().from(USER_ROLES)
                        .where(USER_ROLES.USER_ID.eq(USERS.ID).and(USER_ROLES.ROLE.eq(role.name())))));
    }

    /** Officials holding any official role, whether or not they can sign in. */
    public long countOfficials() {
        return dsl.fetchCount(dsl.selectFrom(USERS)
                .whereExists(dsl.selectOne().from(USER_ROLES)
                        .where(USER_ROLES.USER_ID.eq(USERS.ID)
                                .and(USER_ROLES.ROLE.in(Role.OFFICIAL_ROLES.stream().map(Role::name).toList())))));
    }

    /** Saves the official and replaces their roles with the ones they hold now. */
    @Override
    @Transactional
    public User save(User user) {
        super.save(user);
        dsl.deleteFrom(USER_ROLES).where(USER_ROLES.USER_ID.eq(user.getId())).execute();
        for (Role role : user.getRoles()) {
            dsl.insertInto(USER_ROLES, USER_ROLES.USER_ID, USER_ROLES.ROLE).values(user.getId(), role.name()).execute();
        }
        return user;
    }

    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(USERS.CREATED_AT);
    }

    @Override
    protected User toEntity(UsersRecord r) {
        User u = MAPPER.toEntity(r);
        u.setRoles(rolesOf(r.getId()));
        return u;
    }

    private Set<Role> rolesOf(Long userId) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        dsl.select(USER_ROLES.ROLE).from(USER_ROLES).where(USER_ROLES.USER_ID.eq(userId))
                .fetch(USER_ROLES.ROLE).forEach(role -> roles.add(Role.valueOf(role)));
        return roles;
    }

    @Override
    protected void toRecord(User u, UsersRecord r) {
        MAPPER.toRecord(u, r);
    }

    @Override
    protected Long idOf(User u) {
        return u.getId();
    }

    @Override
    protected void setId(User u, Long id) {
        u.setId(id);
    }
}
