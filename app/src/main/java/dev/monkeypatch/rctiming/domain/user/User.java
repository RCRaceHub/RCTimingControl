package dev.monkeypatch.rctiming.domain.user;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * An official who can sign in (table {@code users}). Their roles are stored in {@code user_roles} and saved with the
 * user by {@link UserRepository}. Only an email and a password hash are kept: no racer logins exist (L10, #18). Never
 * delete one, since audit rows point at it; an admin disables the account instead.
 */
public class User implements CreatedAt, UpdatedAt {

    private Long id;

    private String email;

    /** A one-way hash of the password, never the password. */
    private String passwordHash;

    private String firstName;

    private String lastName;

    private Set<Role> roles = new HashSet<>();

    private Instant createdAt;

    private Instant updatedAt;

    /** When an admin stopped this official signing in (#61); null while they can. */
    private Instant disabledAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public Set<Role> getRoles() { return roles; }

    /** True when the account holds at least one official role, so it may sign in. */
    public boolean isOfficial() { return roles.stream().anyMatch(Role.OFFICIAL_ROLES::contains); }
    public void setRoles(Set<Role> roles) { this.roles = roles; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getDisabledAt() { return disabledAt; }
    public void setDisabledAt(Instant disabledAt) { this.disabledAt = disabledAt; }

    /** True unless an admin has disabled this official. */
    public boolean isEnabled() { return disabledAt == null; }

    /** May sign in and refresh: an official who has not been disabled. */
    public boolean canSignIn() { return isOfficial() && isEnabled(); }

}
