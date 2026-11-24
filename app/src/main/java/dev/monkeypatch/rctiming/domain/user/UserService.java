package dev.monkeypatch.rctiming.domain.user;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

/**
 * Reads officials' accounts, and creates them for the setup wizard. Changing one (roles, password, enabling
 * and disabling) goes through {@link OfficialService}, which also audit-logs it and ends the official's open
 * sessions. There are no racer accounts: competitors never sign in (#18).
 */
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final OfficialService officialService;

    public UserService(UserRepository userRepository, OfficialService officialService) {
        this.userRepository = userRepository;
        this.officialService = officialService;
    }

    /** The official with this email, if any, in a read-only transaction. */
    @ReadTransaction
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /** The official with this id, if any, in a read-only transaction. */
    @ReadTransaction
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Adds an official from the setup wizard. Goes through {@link OfficialService#add} like the Officials
     * screen does, so it is recorded in the officials log with who added them and gets the same checks.
     */
    @Transactional
    public User createStaff(String email, String password, String firstName, String lastName, Set<Role> roles,
                            long actorId) {
        return officialService.add(email, firstName, lastName, password, roles, actorId);
    }

    /**
     * Creates the first admin, before anyone can sign in. Recorded in the officials log with no actor, because
     * there was no one to do it.
     */
    @Transactional
    public User createAdmin(String email, String password, String firstName, String lastName) {
        // A second guard behind SetupService's, so a replayed setup request cannot add another admin
        if (userRepository.count() > 0) {
            throw new StateConflictException("Bootstrap already complete - users exist");
        }
        return officialService.add(email, firstName, lastName, password, Set.of(Role.ADMIN), null);
    }
}
