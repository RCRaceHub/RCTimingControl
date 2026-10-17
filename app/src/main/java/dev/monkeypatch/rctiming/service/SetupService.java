package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.club.ClubProfileRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatTemplateRepository;
import dev.monkeypatch.rctiming.domain.track.TrackRepository;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.domain.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The first-run setup wizard: whether the app has an admin yet, how far setup has got, and the first admin. */
@Service
@Transactional
public class SetupService {

    /** Whether anyone has an account yet (the first admin), and whether the club profile has been saved. */
    public record Status(boolean bootstrapped, boolean setupComplete) {}

    /** Which of the wizard's steps have been done. */
    public record Progress(boolean club, boolean track, boolean format, boolean staff, boolean decoder) {}

    private final ClubProfileRepository clubProfileRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final TrackRepository trackRepository;
    private final RaceFormatTemplateRepository raceFormatTemplateRepository;

    public SetupService(ClubProfileRepository clubProfileRepository,
                        UserRepository userRepository,
                        UserService userService,
                        TrackRepository trackRepository,
                        RaceFormatTemplateRepository raceFormatTemplateRepository) {
        this.clubProfileRepository = clubProfileRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.trackRepository = trackRepository;
        this.raceFormatTemplateRepository = raceFormatTemplateRepository;
    }

    /** Read from the unauthenticated setup endpoint to decide whether to show the wizard or the sign-in page. */
    @Transactional(readOnly = true)
    public Status getStatus() {
        boolean bootstrapped = userRepository.count() > 0;
        boolean setupComplete = clubProfileRepository.count() > 0;
        return new Status(bootstrapped, setupComplete);
    }

    /** Which wizard steps are done, worked out from what is stored, so it survives a restart mid-wizard. */
    @Transactional(readOnly = true)
    public Progress getProgress() {
        boolean club = clubProfileRepository.count() > 0;
        boolean track = trackRepository.count() > 0;
        boolean format = raceFormatTemplateRepository.count() > 0;
        // Staff is complete only when a second official exists beyond the bootstrap admin
        boolean staff = userRepository.countOfficials() >= 2;
        boolean decoder = clubProfileRepository.findCurrent()
                .map(p -> p.getDecoderHost() != null && p.getDecoderPort() != null && p.getDecoderProtocol() != null)
                .orElse(false);
        return new Progress(club, track, format, staff, decoder);
    }

    /**
     * Creates the first admin, on a fresh install only.
     *
     * @throws StateConflictException once anyone has an account
     */
    public User bootstrap(String email, String password, String firstName, String lastName) {
        if (userRepository.count() > 0) {
            throw new StateConflictException("Bootstrap already complete");
        }
        return userService.createAdmin(email, password, firstName, lastName);
    }
}
