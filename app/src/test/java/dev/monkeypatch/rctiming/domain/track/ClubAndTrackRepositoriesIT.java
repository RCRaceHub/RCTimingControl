package dev.monkeypatch.rctiming.domain.track;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.club.ClubAudioSettings;
import dev.monkeypatch.rctiming.domain.club.ClubProfile;
import dev.monkeypatch.rctiming.domain.club.ClubProfileRepository;
import dev.monkeypatch.rctiming.domain.club.GoverningBodyAffiliation;
import dev.monkeypatch.rctiming.domain.club.GoverningBodyAffiliationRepository;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static dev.monkeypatch.rctiming.persistence.RoundTrip.assertSavedAndReloaded;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The club and track repositories save and load every field (#70). */
class ClubAndTrackRepositoriesIT extends AbstractIntegrationTest {

    private static final Instant T1 = Instant.parse("2026-10-05T10:15:30.123456Z");
    private static final Instant T2 = T1.plus(1, ChronoUnit.HOURS);

    @Autowired ClubProfileRepository clubProfiles;
    @Autowired GoverningBodyAffiliationRepository affiliations;
    @Autowired TrackRepository tracks;
    @Autowired DecoderLoopRepository loops;
    @Autowired TrackLapThresholdRepository thresholds;
    @Autowired RacingClassRepository racingClasses;

    private final List<Runnable> cleanup = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        cleanup.reversed().forEach(Runnable::run);
    }

    @Test
    void clubProfile() {
        ClubProfile p = new ClubProfile();
        p.setName("Test club");
        p.setEmail("club@example.com");
        p.setPhone("01234 567890");
        p.setWebsiteUrl("https://example.com");
        p.setLatitude(53.123456789);
        p.setLongitude(-2.987654321);
        p.setTimezone("Europe/London");
        p.setLogoUrl("/logo.png");
        p.setAudioSettings(new ClubAudioSettings(false, false, false, false, false, 5, new int[] {90}));
        p.setDefaultVoiceId("voice-a");
        p.setDecoderHost("10.0.0.5");
        p.setDecoderPort(5100);
        p.setDecoderProtocol("RC4");
        p.setCreatedAt(T1);
        p.setUpdatedAt(T1);

        ClubProfile saved = assertSavedAndReloaded(clubProfiles, p, c -> {
            c.setName("Renamed club");
            c.setEmail("other@example.com");
            c.setPhone("09876");
            c.setWebsiteUrl("https://example.org");
            c.setLatitude(51.5);
            c.setLongitude(0.1);
            c.setTimezone("UTC");
            c.setLogoUrl("/other.svg");
            c.setAudioSettings(ClubAudioSettings.defaults());
            c.setDefaultVoiceId("voice-b");
            c.setDecoderHost("10.0.0.6");
            c.setDecoderPort(5403);
            c.setDecoderProtocol("P3");
            c.setCreatedAt(T2);
            c.setUpdatedAt(T2);
            return c;
        }, ClubProfile::getId);
        cleanup.add(() -> clubProfiles.deleteById(saved.getId()));
    }

    @Test
    void governingBodyAffiliation() {
        GoverningBodyAffiliation a = affiliation("RT-" + System.nanoTime());
        GoverningBodyAffiliation saved = assertSavedAndReloaded(affiliations, a, c -> {
            c.setCode(c.getCode() + "-B");
            c.setDisplayName("Other body");
            c.setMembershipRequired(false);
            c.setCreatedAt(T2);
            return c;
        }, GoverningBodyAffiliation::getId);
        cleanup.add(() -> affiliations.deleteById(saved.getId()));
    }

    @Test
    void aTakenUniqueValueIsADuplicateKey() {
        String code = "DUP-" + System.nanoTime();
        GoverningBodyAffiliation first = affiliations.save(affiliation(code));
        cleanup.add(() -> affiliations.deleteById(first.getId()));

        assertThatThrownBy(() -> affiliations.save(affiliation(code)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void trackLoadsItsLoopsAndThresholdsAndDeletesThemWithIt() {
        Track t = new Track();
        t.setName("Test track");
        t.setVenueNotes("Behind the clubhouse");
        t.setTrackLength(312.75);
        t.setCreatedAt(T1);
        t.setUpdatedAt(T1);
        Track track = assertSavedAndReloaded(tracks, t, c -> {
            c.setName("Renamed track");
            c.setVenueNotes(null);
            c.setTrackLength(250.5);
            c.setCreatedAt(T2);
            c.setUpdatedAt(T2);
            return c;
        }, Track::getId);
        cleanup.add(() -> tracks.deleteById(track.getId()));

        RacingClass rc = new RacingClass();
        rc.setName("Round trip class " + System.nanoTime());
        rc.setCreatedAt(T1);
        rc.setUpdatedAt(T1);
        RacingClass racingClass = racingClasses.save(rc);
        cleanup.add(() -> racingClasses.deleteById(racingClass.getId()));

        DecoderLoop l = new DecoderLoop();
        l.setTrackId(track.getId());
        l.setLoopId("1");
        l.setDisplayName("Finish");
        l.setLoopType(LoopType.CHICANE);
        l.setScoringLoop(false);
        l.setCreatedAt(T1);
        DecoderLoop loop = assertSavedAndReloaded(loops, l, c -> {
            c.setLoopId("2");
            c.setDisplayName("Split");
            c.setLoopType(LoopType.OTHER);
            c.setScoringLoop(true);
            c.setCreatedAt(T2);
            return c;
        }, DecoderLoop::getId);

        TrackLapThreshold th = new TrackLapThreshold();
        th.setTrackId(track.getId());
        th.setRacingClassId(racingClass.getId());
        th.setMinLapMs(12000);
        th.setMaxLastLapMs(60000);
        th.setCreatedAt(T1);
        TrackLapThreshold threshold = assertSavedAndReloaded(thresholds, th, c -> {
            c.setRacingClassId(null);
            c.setMinLapMs(9000);
            c.setMaxLastLapMs(null);
            c.setCreatedAt(T2);
            return c;
        }, TrackLapThreshold::getId, "racingClassName");
        assertThat(thresholds.findByTrackIdAndRacingClassIsNull(track.getId())).isPresent();

        threshold.setRacingClassId(racingClass.getId());
        thresholds.save(threshold);
        assertThat(thresholds.findByTrackIdAndRacingClassId(track.getId(), racingClass.getId()))
                .get().extracting(TrackLapThreshold::getRacingClassName).isEqualTo(racingClass.getName());

        Track loaded = tracks.findById(track.getId()).orElseThrow();
        assertThat(loaded.getDecoderLoops()).extracting(DecoderLoop::getId).containsExactly(loop.getId());
        assertThat(loaded.getLapThresholds()).extracting(TrackLapThreshold::getId).containsExactly(threshold.getId());

        tracks.deleteById(track.getId());
        assertThat(loops.existsById(loop.getId())).isFalse();
        assertThat(thresholds.existsById(threshold.getId())).isFalse();
    }

    @Test
    void savingATrackSavesItsLoopsAndThresholdsAndRemovesDroppedOnes() {
        Track t = new Track();
        t.setName("Aggregate track");
        t.setCreatedAt(T1);
        t.setUpdatedAt(T1);
        DecoderLoop finish = new DecoderLoop();
        finish.setLoopId("1");
        finish.setDisplayName("Finish");
        finish.setCreatedAt(T1);
        DecoderLoop split = new DecoderLoop();
        split.setLoopId("2");
        split.setDisplayName("Split");
        split.setCreatedAt(T1);
        TrackLapThreshold threshold = new TrackLapThreshold();
        threshold.setMinLapMs(10000);
        threshold.setCreatedAt(T1);
        t.getDecoderLoops().addAll(List.of(finish, split));
        t.getLapThresholds().add(threshold);

        Track saved = tracks.save(t);
        cleanup.add(() -> tracks.deleteById(saved.getId()));
        Track loaded = tracks.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getDecoderLoops()).extracting(DecoderLoop::getDisplayName).containsExactly("Finish", "Split");
        assertThat(loaded.getDecoderLoops()).extracting(DecoderLoop::getTrackId).containsOnly(saved.getId());
        assertThat(loaded.getLapThresholds()).extracting(TrackLapThreshold::getMinLapMs).containsExactly(10000);

        loaded.getDecoderLoops().get(0).setDisplayName("Start/finish");
        loaded.getDecoderLoops().remove(1);
        loaded.getLapThresholds().clear();
        tracks.save(loaded);

        Track reloaded = tracks.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getDecoderLoops()).extracting(DecoderLoop::getDisplayName).containsExactly("Start/finish");
        assertThat(reloaded.getLapThresholds()).isEmpty();
        assertThat(loops.findByTrackId(saved.getId())).hasSize(1);
        assertThat(thresholds.findByTrackId(saved.getId())).isEmpty();
    }

    private static GoverningBodyAffiliation affiliation(String code) {
        GoverningBodyAffiliation a = new GoverningBodyAffiliation();
        a.setCode(code);
        a.setDisplayName("Test body");
        a.setMembershipRequired(true);
        a.setCreatedAt(T1);
        return a;
    }
}
