package dev.monkeypatch.rctiming.persistence;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Where the app keeps its data when no folder is configured.
 *
 * <p>Run by hand, that is the usual per-user app-data folder. The installed packages (#23) run the
 * app as a background service, so their launcher sets {@code -Drctiming.data-scope=machine} and
 * the data goes in the per-machine folder instead, outside the install folder so upgrades keep it.
 */
public final class DataDirectories {

    /** System property the installed launcher sets; {@code machine} picks the per-machine folder. */
    public static final String SCOPE_PROPERTY = "rctiming.data-scope";

    private static final String APP_FOLDER = "RCTimingControl";

    private DataDirectories() {
    }

    /** The default data folder for this operating system and scope, from the JVM's OS, home and environment. */
    public static Path defaultDirectory() {
        return defaultDirectory(System.getProperty("os.name", ""), System.getProperty("user.home"),
                System.getenv(), isMachineScope());
    }

    /** True when the launcher set {@value #SCOPE_PROPERTY} to {@code machine}, as the installed service does. */
    public static boolean isMachineScope() {
        return "machine".equalsIgnoreCase(System.getProperty(SCOPE_PROPERTY, "").trim());
    }

    /**
     * The pure form of {@link #defaultDirectory()}, taking the system facts as arguments so tests can pass each
     * operating system's. Windows uses {@code ProgramData} (machine) or {@code LOCALAPPDATA} (user), macOS
     * {@code Application Support}, and Linux {@code /var/lib} (machine) or {@code XDG_DATA_HOME} falling back to
     * {@code ~/.local/share}.
     */
    static Path defaultDirectory(String osName, String home, Map<String, String> env, boolean machine) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String root = machine ? env.get("ProgramData") : env.get("LOCALAPPDATA");
            if (root == null || root.isBlank()) {
                root = machine ? "C:\\ProgramData" : home;
            }
            return Path.of(root, APP_FOLDER);
        }
        if (os.contains("mac")) {
            return machine
                    ? Path.of("/Library", "Application Support", APP_FOLDER)
                    : Path.of(home, "Library", "Application Support", APP_FOLDER);
        }
        String linuxFolder = APP_FOLDER.toLowerCase(Locale.ROOT);
        if (machine) {
            return Path.of("/var", "lib", linuxFolder);
        }
        String xdgDataHome = env.get("XDG_DATA_HOME");
        return xdgDataHome != null && !xdgDataHome.isBlank()
                ? Path.of(xdgDataHome, linuxFolder)
                : Path.of(home, ".local", "share", linuxFolder);
    }
}
