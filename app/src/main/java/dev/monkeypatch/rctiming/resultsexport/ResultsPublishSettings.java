package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import org.springframework.stereotype.Component;

/**
 * The club's choice of where results are published (#319). A club that has not chosen gets RaceHub when a
 * RaceHub address is set, which is how results behaved before the choice existed, and nothing otherwise.
 */
@Component
public class ResultsPublishSettings {

    private final ClubProfileService clubProfileService;
    private final RaceHubResultsEndpoint endpoint;

    public ResultsPublishSettings(ClubProfileService clubProfileService, RaceHubResultsEndpoint endpoint) {
        this.clubProfileService = clubProfileService;
        this.endpoint = endpoint;
    }

    /** What the club chose, or null when it has not. */
    public ResultsPublishTarget chosen() {
        return clubProfileService.getResultsPublishTarget().map(ResultsPublishTarget::valueOf).orElse(null);
    }

    /** What applies now: the choice, else the default. */
    public ResultsPublishTarget effective() {
        ResultsPublishTarget chosen = chosen();
        if (chosen != null) {
            return chosen;
        }
        return endpoint.resultsUrl() != null ? ResultsPublishTarget.RACEHUB : ResultsPublishTarget.NONE;
    }
}
