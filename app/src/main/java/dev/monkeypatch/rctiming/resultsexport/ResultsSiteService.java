package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.competitor.PublicNames;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventCompleted;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import dev.monkeypatch.rctiming.query.resultsexport.ResultsExportQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The results site for clubs without RaceHub (#359, the {@code SITE} choice of the publish setting, #319): static
 * pages built by {@link ResultsSiteGenerator} from the same documents Results Export v1 sends, so the site and
 * RaceHub always agree.
 *
 * <p>Two ways to get the pages. An admin can download them as a zip at any time ({@link #zip()}, any publish choice).
 * And when the club publishes to the site and {@code rctiming.results-site.directory} names a folder, the pages are
 * rewritten there a few seconds after a race finishes, a finished race is corrected or the race day closes, and once
 * after start-up; a club points that folder at a synced or mounted location for its web space. The rewrite runs on a
 * scheduler thread, never in race control's request, and a failure is logged and tried again on the next pass.
 *
 * <p>The "something changed" mark is in memory and is set after the commit that changed the results, so a restart
 * costs at most one extra rewrite and a rewrite never reads results that are not committed yet. Some changes raise
 * no mark (a race restarted, a competitor's public name setting changed, an event renamed), so the folder is also
 * rewritten every {@code rctiming.results-site.refresh-minutes} (5 by default), and pages of events that no longer
 * appear are removed. Only the pages the service itself writes are touched.
 */
@Service
@EnableConfigurationProperties(ResultsSiteProperties.class)
public class ResultsSiteService {

    private static final Logger log = LoggerFactory.getLogger(ResultsSiteService.class);

    private final EventRepository eventRepository;
    private final ResultsExportQuery exportQuery;
    private final ResultsPublishSettings publishSettings;
    private final PublicNames publicNames;
    private final Path directory;
    /** Starts true so the folder is brought up to date once after start-up. */
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private final Duration refreshEvery;
    private volatile Instant lastWrite = Instant.EPOCH;
    private final Clock clock;

    @Autowired
    public ResultsSiteService(EventRepository eventRepository, ResultsExportQuery exportQuery,
                              ResultsPublishSettings publishSettings, PublicNames publicNames,
                              ResultsSiteProperties site) {
        this(eventRepository, exportQuery, publishSettings, publicNames, site.directory(), site.refreshMinutes(),
                Clock.systemUTC());
    }

    ResultsSiteService(EventRepository eventRepository, ResultsExportQuery exportQuery,
                       ResultsPublishSettings publishSettings, PublicNames publicNames, String directory,
                       long refreshMinutes, Clock clock) {
        this.clock = clock;
        this.refreshEvery = Duration.ofMinutes(Math.max(1, refreshMinutes));
        this.eventRepository = eventRepository;
        this.exportQuery = exportQuery;
        this.publishSettings = publishSettings;
        this.publicNames = publicNames;
        this.directory = directory == null || directory.isBlank() ? null : Path.of(directory.trim());
    }

    /** A race finished or was abandoned, once its transaction has committed. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRaceStatusChanged(RaceStatusChangedEvent event) {
        if (event.getNewStatus() == RaceStatus.FINISHED) {
            dirty.set(true);
        }
    }

    /** A finished race was corrected, once its transaction has committed. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onFinishedRaceCorrected(FinishedRaceCorrected event) {
        dirty.set(true);
    }

    /** The race day closed, once its transaction has committed. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDayClosed(EventCompleted event) {
        dirty.set(true);
    }

    /**
     * Every page of the site as it stands now, by relative path. Reads each event in its own snapshot, so a race
     * finishing during the build can appear in one event's page and not another's, which the next rewrite fixes.
     */
    public Map<String, String> pages() {
        List<ResultsExportV1> exports = new ArrayList<>();
        String clubName = null;
        PublicNames.Snapshot names = publicNames.snapshot();
        for (Event event : eventRepository.findAll()) {
            try {
                ResultsExportV1 export = exportQuery.build(event.getId(), event.getResultsExportRevision(),
                        clock.instant());
                clubName = export.source().clubName();
                exports.add(withPublicNames(export, names));
            } catch (EntityNotFoundException e) {
                log.debug("Event {} was deleted while the results site was built; left out", event.getId());
            }
        }
        return ResultsSiteGenerator.generate(exports, clubName);
    }

    /**
     * The export with every name as the public may see it (#265). The export carries the full name, which RaceHub
     * needs to place a driver, but a web page anyone can open follows each competitor's public name setting.
     */
    static ResultsExportV1 withPublicNames(ResultsExportV1 export, PublicNames.Snapshot names) {
        List<ResultsExportV1.Race> races = export.races().stream()
                .map(race -> new ResultsExportV1.Race(race.rctcRaceId(), race.rctcEventClassId(),
                        race.racehubEventClassIds(), race.className(), race.roundType(), race.roundNumber(),
                        race.heatNumber(), race.finalLetter(), race.status(), race.finishedAt(),
                        race.results().stream()
                                .map(r -> new ResultsExportV1.Row(r.position(), r.externalSource(), r.entryId(),
                                        r.driverProfileId(), r.racehubEventClassId(), r.rctcEntryId(),
                                        r.rctcCompetitorId(),
                                        names.of(r.rctcCompetitorId(), r.displayName(), r.carNumber()),
                                        r.carNumber(), r.laps(), r.totalTimeMs(), r.bestLapMs(), r.penalties()))
                                .toList()))
                .toList();
        List<ResultsExportV1.Championship> championships = export.championships().stream()
                .map(c -> new ResultsExportV1.Championship(c.rctcChampionshipId(), c.name(), c.roundNumber(),
                        c.classes().stream()
                                .map(cls -> new ResultsExportV1.ChampionshipClass(cls.rctcRacingClassId(),
                                        cls.className(),
                                        cls.standings().stream()
                                                .map(s -> new ResultsExportV1.Standing(s.position(),
                                                        s.externalSource(), s.driverProfileId(),
                                                        s.rctcCompetitorId(),
                                                        names.of(s.rctcCompetitorId(), s.displayName(), null),
                                                        s.totalPoints(), s.roundPosition(), s.roundPoints(),
                                                        s.roundDropped(), s.roundExcluded()))
                                                .toList()))
                                .toList()))
                .toList();
        return new ResultsExportV1(export.schemaVersion(), export.revision(), export.generatedAt(), export.source(),
                export.event(), races, championships);
    }

    /** The site as a zip file, for an admin to upload to the club's web space. */
    public byte[] zip() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> page : pages().entrySet()) {
                zip.putNextEntry(new ZipEntry(page.getKey()));
                zip.write(page.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not build the results site zip", e);
        }
        return bytes.toByteArray();
    }

    /**
     * Rewrites the folder when results changed since the last pass, the club publishes to the site and a folder is
     * set. Each page is written to a temporary file and moved into place, so a sync tool never uploads half a page.
     * Runs on a scheduler thread; never throws.
     */
    @Scheduled(initialDelay = 20_000, fixedDelayString = "${rctiming.results-site.interval-ms:5000}")
    public void writeIfChanged() {
        if (directory == null || !publishSettings.effective().includesSite()) {
            return;
        }
        boolean stale = lastWrite.plus(refreshEvery).isBefore(clock.instant());
        if (!dirty.compareAndSet(true, false) && !stale) {
            return;
        }
        try {
            Map<String, String> pages = pages();
            for (Map.Entry<String, String> page : pages.entrySet()) {
                write(page.getKey(), page.getValue());
            }
            removeStaleEventPages(pages.keySet());
            lastWrite = clock.instant();
            log.info("Results site written to {}", directory);
        } catch (IOException | RuntimeException e) {
            dirty.set(true);
            log.error("Could not write the results site to {}; will try again", directory, e);
        }
    }

    /** Deletes {@code events/<id>.html} pages left by events that no longer have results. */
    private void removeStaleEventPages(java.util.Set<String> current) throws IOException {
        Path events = directory.resolve("events");
        if (!Files.isDirectory(events)) {
            return;
        }
        try (var files = Files.list(events)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.matches("\\d+\\.html") && !current.contains("events/" + name)) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private void write(String relativePath, String html) throws IOException {
        Path target = directory.resolve(relativePath).normalize();
        if (!target.startsWith(directory.normalize())) {
            throw new IOException("Page path leaves the results site folder: " + relativePath);
        }
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".page-", ".tmp");
        try {
            Files.writeString(temp, html, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
