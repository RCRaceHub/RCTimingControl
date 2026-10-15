package dev.monkeypatch.rctiming;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps personal data out of the application log (#266). The log is a plain file in the data folder that anyone
 * helping the club may be sent, so a log call must not name an email, a display name, a spoken name, a BRCA number,
 * a password or a token. Ids and transponder numbers are fine: they say which record, not who.
 *
 * <p>This looks only at what a log call is given in the source, so it cannot see a message that carries a name inside
 * an exception; keep such messages free of names too.
 */
class LogContentTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java/dev/monkeypatch/rctiming");
    private static final Pattern LOG_CALL = Pattern.compile("\\blog\\.(trace|debug|info|warn|error)\\(");
    private static final Pattern PERSONAL = Pattern.compile(
            "(?i)\\b(get)?(email|displayName|spokenName|brca\\w*|password\\w*|rawToken|refreshToken)\\b");

    @Test
    void noLogCallNamesPersonalData() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                violations.addAll(violations(MAIN_SOURCES.relativize(file).toString(), Files.readString(file)));
            }
        }
        assertThat(violations).as("log calls that name personal data").isEmpty();
    }

    @Test
    void aLogCallWithAnEmailOrANameIsCaught() {
        assertThat(violations("Example.java", "class A { void f() {\n    log.info(\"Signed in {}\", user.getEmail());\n} }"))
                .singleElement().asString().contains("Example.java:2");
        assertThat(violations("Example.java", "log.warn(\"Merged {}\",\n        competitor.getDisplayName());"))
                .singleElement().asString().contains("Example.java:1");
        assertThat(violations("Example.java", "log.info(\"Race {} started\", raceId);")).isEmpty();
        assertThat(violations("Example.java", "String email = a;\nlog.info(\"Race {}\", raceId);")).isEmpty();
    }

    private static List<String> violations(String file, String source) {
        List<String> found = new ArrayList<>();
        Matcher call = LOG_CALL.matcher(source);
        while (call.find()) {
            int end = source.indexOf(");", call.end());
            String text = source.substring(call.end(), end < 0 ? source.length() : end);
            if (PERSONAL.matcher(text).find()) {
                int line = 1 + (int) source.substring(0, call.start()).chars().filter(c -> c == '\n').count();
                found.add(file + ":" + line);
            }
        }
        return found;
    }
}
