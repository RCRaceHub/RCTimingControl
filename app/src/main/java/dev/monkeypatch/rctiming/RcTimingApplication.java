package dev.monkeypatch.rctiming;

import dev.monkeypatch.rctiming.backup.RestoreCommand;
import dev.monkeypatch.rctiming.persistence.DataDirectories;
import dev.monkeypatch.rctiming.security.ResetAdminPasswordCommand;
import dev.monkeypatch.rctiming.simulator.SimulatorMain;
import dev.monkeypatch.rctiming.simulator.relay.LiveFeedRelayMain;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;
import java.util.Arrays;

/**
 * The app, and its command-line tools. {@code restore}, {@code reset-admin-password}, {@code relay} and
 * {@code simulate} run instead of the server, before its Spring context starts: a restore replaces the
 * database file, so nothing may have it open; resetting a password is for when no one can sign in, and works
 * on the database directly. Those two start a small context of their own that only reads the database settings.
 * The relay and the simulator are separate programs that only share the jar.
 * {@code restore} and {@code reset-admin-password} exit with 0 when they succeed, 1 when they fail and 2 when
 * the arguments are wrong.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class RcTimingApplication {

    /**
     * Picks a command-line tool by the first argument, or starts the server. When the installer has set the
     * machine-wide data scope, the data folder is wired in first so every mode finds the same database and log.
     * {@code restore} and {@code reset-admin-password} end the JVM with their exit code; {@code relay} and
     * {@code simulate} run their own programs and return when they stop.
     */
    public static void main(String[] args) throws Exception {
        if (DataDirectories.isMachineScope()) {
            useMachineFolders();
        }
        if (args.length > 0 && args[0].equals("restore")) {
            System.exit(RestoreCommand.run(Arrays.copyOfRange(args, 1, args.length)));
        }
        if (args.length > 0 && args[0].equals("reset-admin-password")) {
            // The way back in for a club whose only admin is locked out (#61)
            System.exit(ResetAdminPasswordCommand.run(Arrays.copyOfRange(args, 1, args.length)));
        }
        if (args.length > 0 && args[0].equals("relay")) {
            // A stand-in relay and viewer page for trying the live feed (#28)
            LiveFeedRelayMain.main(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (args.length > 0 && args[0].equals("simulate")) {
            SimulatorMain.main(simulatorArguments(Arrays.copyOfRange(args, 1, args.length)));
            return;
        }
        SpringApplication.run(RcTimingApplication.class, args);
    }

    /**
     * The installed service (#23) has no console and no working folder of its own, so it logs to a
     * file in the data folder and reads optional settings (port, bind address and so on) from an
     * {@code application.properties} or {@code application.yml} placed there.
     */
    static void useMachineFolders() {
        String configured = System.getenv("RCTIMING_DATA_DIR");
        Path dataDirectory = configured != null && !configured.isBlank()
                ? Path.of(configured)
                : DataDirectories.defaultDirectory();
        String folderUri = dataDirectory.toAbsolutePath().toUri().toString();
        setIfAbsent("spring.config.additional-location",
                "optional:" + (folderUri.endsWith("/") ? folderUri : folderUri + "/"));
        setIfAbsent("logging.file.name", dataDirectory.resolve("logs").resolve("rctiming.log").toString());
    }

    /**
     * With no arguments, {@code simulate} plays the demo club's eight transponders (#24), so
     * {@code RCTimingControl simulate} next to the demo data gives live laps straight away.
     */
    static String[] simulatorArguments(String[] given) {
        return given.length > 0 ? given : new String[] {
                "--mode=generative", "--port=5100", "--transponders=101,102,103,104,105,106,107,108",
                "--interval-ms=13000", "--jitter-ms=2500"};
    }

    /** Sets a system property unless the operator already did, on the command line or in the service definition. */
    private static void setIfAbsent(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
