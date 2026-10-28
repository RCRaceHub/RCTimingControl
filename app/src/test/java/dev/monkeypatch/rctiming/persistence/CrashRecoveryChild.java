package dev.monkeypatch.rctiming.persistence;

import dev.monkeypatch.rctiming.RcTimingApplication;
import dev.monkeypatch.rctiming.domain.practice.PracticeLapRepository;
import dev.monkeypatch.rctiming.practice.PracticeSessionService;
import dev.monkeypatch.rctiming.timing.LapPassingEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The app as {@link CrashRecoveryIT} runs it in a child JVM, on the database folder given as the
 * first argument. "write" times a practice session for ever, printing each lap once it is
 * committed, until the parent kills the process. "count" starts the app again on the same folder
 * and prints how many practice laps survived.
 */
public final class CrashRecoveryChild {

    static final String COMMITTED = "COMMITTED ";
    static final String SURVIVED = "SURVIVED ";

    private CrashRecoveryChild() {
    }

    public static void main(String[] args) {
        String mode = args[1];
        ConfigurableApplicationContext context = SpringApplication.run(RcTimingApplication.class,
                "--rctiming.database.data-directory=" + args[0],
                "--spring.main.web-application-type=none",
                "--app.decoder.listener.enabled=false",
                "--tts.enabled=false",
                "--logging.level.root=WARN");
        PracticeLapRepository laps = context.getBean(PracticeLapRepository.class);
        if (mode.equals("count")) {
            System.out.println(SURVIVED + laps.count());
            System.out.flush();
            context.close();
            return;
        }

        PracticeSessionService sessions = context.getBean(PracticeSessionService.class);
        long sessionId = sessions.create(dev.monkeypatch.rctiming.domain.audit.Actor.system("test"), "Crash", null, 3, null).id();
        sessions.start(dev.monkeypatch.rctiming.domain.audit.Actor.system("test"), sessionId);
        for (long lap = 0; ; lap++) {
            // The listener's transaction has committed by the time publishEvent returns
            context.publishEvent(new LapPassingEvent(0, "777", lap * 20_000_000L));
            if (lap > 0) {
                System.out.println(COMMITTED + lap);
                System.out.flush();
            }
        }
    }
}
