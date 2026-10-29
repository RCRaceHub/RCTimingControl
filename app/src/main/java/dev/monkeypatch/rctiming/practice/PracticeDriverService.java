package dev.monkeypatch.rctiming.practice;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorService;
import dev.monkeypatch.rctiming.domain.competitor.PossibleDuplicateCompetitorException;
import dev.monkeypatch.rctiming.domain.practice.PracticeDriver;
import dev.monkeypatch.rctiming.domain.practice.PracticeDriverRepository;
import dev.monkeypatch.rctiming.domain.practice.PracticeSession;
import dev.monkeypatch.rctiming.domain.practice.PracticeSessionRepository;
import dev.monkeypatch.rctiming.persistence.AfterCommit;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.practice.dto.PracticeDriverDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Names the transponders seen in a practice session (#151). A transponder is matched to an existing competitor, or
 * to a new walk-in competitor made on the spot, and the match is saved with the session: it lasts, it names the
 * transponder's earlier laps too, and it works with no event. A new competitor is a full one, so it can be matched
 * to an entry in a later event.
 */
@Service
public class PracticeDriverService {

    private static final int MAX_TRANSPONDER_LENGTH = 50;
    private static final int MAX_NAME_LENGTH = 100;

    private final PracticeSessionRepository sessionRepository;
    private final PracticeDriverRepository driverRepository;
    private final CompetitorRepository competitorRepository;
    private final CompetitorService competitorService;
    private final PracticeTimingService timingService;
    private final AuditService audit;

    public PracticeDriverService(PracticeSessionRepository sessionRepository,
                                 PracticeDriverRepository driverRepository,
                                 CompetitorRepository competitorRepository,
                                 CompetitorService competitorService,
                                 PracticeTimingService timingService,
                                 AuditService audit) {
        this.sessionRepository = sessionRepository;
        this.driverRepository = driverRepository;
        this.competitorRepository = competitorRepository;
        this.competitorService = competitorService;
        this.timingService = timingService;
        this.audit = audit;
    }

    /**
     * Names a transponder in a session, replacing any name it already had there. Give {@code competitorId} for an
     * existing competitor, or {@code newName} to make a walk-in: a name that matches someone who already exists is
     * refused (409 with the matches) unless {@code confirmNewCompetitor} says it is a different person.
     *
     * <p>The match and its audit row are saved in one transaction. The running session's live table is told only
     * after the commit, so it never shows a name that was rolled back.
     *
     * @throws IllegalArgumentException if the transponder number is blank or too long, or neither a competitor nor
     *                                  a usable new name is given
     */
    @Transactional
    public PracticeDriverDto link(Actor actor, Long sessionId, LinkRequest request) {
        PracticeSession session = sessionRepository.getOrThrow(sessionId);
        String transponder = request.transponderNumber() == null ? "" : request.transponderNumber().trim();
        if (transponder.isEmpty() || transponder.length() > MAX_TRANSPONDER_LENGTH) {
            throw new IllegalArgumentException(
                    "A transponder number is needed, at most " + MAX_TRANSPONDER_LENGTH + " characters");
        }
        Competitor competitor = request.competitorId() != null
                ? competitorRepository.getOrThrow(request.competitorId())
                : newWalkIn(request);

        PracticeDriver driver = driverRepository
                .findByPracticeSessionIdAndTransponderNumber(sessionId, transponder)
                .orElseGet(() -> {
                    PracticeDriver created = new PracticeDriver();
                    created.setPracticeSessionId(sessionId);
                    created.setTransponderNumber(transponder);
                    return created;
                });
        Long before = driver.getCompetitorId();
        driver.setCompetitorId(competitor.getId());
        driverRepository.save(driver);

        audit.entry(actor, "PRACTICE_TRANSPONDER_LINKED").entity("practice_session", sessionId)
                .event(session.getEventId())
                .summary("Named transponder " + transponder + " as " + competitor.getDisplayName()
                        + " in the practice session " + session.getName())
                .before(before == null ? null : Map.of("competitorId", before))
                .after(Map.of("transponderNumber", transponder, "competitorId", competitor.getId()))
                .record();

        String name = competitor.getDisplayName();
        AfterCommit.run(() -> timingService.nameTransponder(sessionId, transponder, name));
        return new PracticeDriverDto(transponder, competitor.getId(), name);
    }

    /** The transponders named in the session, with the competitor each was matched to. */
    @ReadTransaction
    public List<PracticeDriverDto> list(Long sessionId) {
        return driverRepository.findByPracticeSessionId(sessionId).stream()
                .map(d -> new PracticeDriverDto(d.getTransponderNumber(), d.getCompetitorId(),
                        competitorRepository.findById(d.getCompetitorId())
                                .map(Competitor::getDisplayName).orElse(null)))
                .toList();
    }

    private Competitor newWalkIn(LinkRequest request) {
        String name = request.newName() == null ? "" : request.newName().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Choose a competitor or give a name for a new one");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("A name can be at most " + MAX_NAME_LENGTH + " characters");
        }
        List<Competitor> possible = competitorService.findPossibleDuplicates(name);
        if (!possible.isEmpty() && !Boolean.TRUE.equals(request.confirmNewCompetitor())) {
            throw new PossibleDuplicateCompetitorException(possible);
        }
        return competitorService.createWalkIn(name);
    }

    /** Request body for naming a transponder; see {@link #link}. */
    public record LinkRequest(String transponderNumber, Long competitorId, String newName,
                              Boolean confirmNewCompetitor) {}
}
