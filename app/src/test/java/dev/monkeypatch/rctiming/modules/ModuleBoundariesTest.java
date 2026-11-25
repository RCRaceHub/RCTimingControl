package dev.monkeypatch.rctiming.modules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps modules apart (#798). A package marked {@link ModulePackage} is a module; the rules, checked on the source
 * files like {@code LayeringTest}:
 * <ol>
 * <li>A module does not import another module's package: it depends on core and talks through core events.</li>
 * <li>Core does not import a module's package. The one exception is the module's own {@code @Bean ModuleDescriptor},
 * which sits inside the module, so core needs none.</li>
 * <li>An event listener, scheduled job or message handler in a module mentions {@code ModuleSwitch}, so it is idle
 * while the module is off. {@code @RequiresModule} covers controllers and is not enough for these.</li>
 * </ol>
 * No feature is a module yet, so these rules pass with nothing to check; the fixtures below show what they catch.
 */
class ModuleBoundariesTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final String ROOT = "dev.monkeypatch.rctiming";
    private static final Pattern PACKAGE_MARK = Pattern.compile("@ModulePackage\\(\\s*\"([a-z][a-z0-9-]*)\"\\s*\\)");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+?)(?:\\.\\*)?;", Pattern.MULTILINE);
    private static final Pattern ENTRY_POINT =
            Pattern.compile("@(EventListener|TransactionalEventListener|Scheduled|MessageMapping|SubscribeMapping)\\b");

    /** Module package (dotted, under the root) to module id, read from the package-info files. */
    static Map<String, String> modulePackages(Path main) throws IOException {
        Map<String, String> found = new TreeMap<>();
        try (Stream<Path> files = Files.walk(main)) {
            for (Path file : files.filter(p -> p.getFileName().toString().equals("package-info.java")).toList()) {
                Matcher mark = PACKAGE_MARK.matcher(Files.readString(file));
                if (mark.find()) {
                    found.put(main.relativize(file.getParent()).toString().replace('/', '.'), mark.group(1));
                }
            }
        }
        return found;
    }

    /** The module that owns a package (or one below it), or null for core. */
    static String moduleOf(String pkg, Map<String, String> modules) {
        return modules.entrySet().stream()
                .filter(e -> pkg.equals(e.getKey()) || pkg.startsWith(e.getKey() + "."))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    static List<String> violations(String pkg, String file, String source, Map<String, String> modules) {
        List<String> found = new ArrayList<>();
        String owner = moduleOf(pkg, modules);
        Matcher imports = IMPORT.matcher(source);
        while (imports.find()) {
            String imported = imports.group(1);
            String importedPackage = imported.contains(".") ? imported.substring(0, imported.lastIndexOf('.')) : imported;
            String target = moduleOf(importedPackage, modules);
            if (target == null || target.equals(owner)) {
                continue;
            }
            found.add(file + ": " + (owner == null ? "core" : "module " + owner) + " imports module " + target + " (" + imported + ")");
        }
        if (owner != null && ENTRY_POINT.matcher(source).find() && !source.contains("ModuleSwitch")) {
            found.add(file + ": a listener, job or message handler in module " + owner + " never checks ModuleSwitch");
        }
        return found;
    }

    @Test
    void modulesStayApartAndAreIdleWhenOff() throws IOException {
        Map<String, String> modules = modulePackages(MAIN);
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN.resolve(ROOT.replace('.', '/')))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String pkg = MAIN.relativize(file.getParent()).toString().replace('/', '.');
                violations.addAll(violations(pkg, MAIN.relativize(file).toString(), Files.readString(file), modules));
            }
        }
        assertThat(violations).as("module boundary breaches").isEmpty();
    }

    private static final Map<String, String> FIXTURE = Map.of(
            ROOT + ".screens", "screens", ROOT + ".practice", "practice");

    @Test
    void aModuleImportingAnotherModuleIsCaught() {
        assertThat(violations(ROOT + ".screens", "A.java",
                "import " + ROOT + ".practice.PracticeService;\n", FIXTURE)).singleElement().asString()
                .contains("module screens imports module practice");
    }

    @Test
    void coreImportingAModuleIsCaughtButAModuleMayUseCoreAndItself() {
        assertThat(violations(ROOT + ".domain.race", "B.java",
                "import " + ROOT + ".screens.ScreenService;\n", FIXTURE)).singleElement().asString()
                .contains("core imports module screens");
        assertThat(violations(ROOT + ".screens.viewer", "C.java",
                "import " + ROOT + ".screens.ScreenService;\nimport " + ROOT + ".domain.race.Race;\n", FIXTURE)).isEmpty();
    }

    @Test
    void aListenerWithoutTheSwitchIsCaught() {
        assertThat(violations(ROOT + ".screens", "D.java", "@EventListener\nvoid on(Foo f) {}\n", FIXTURE))
                .singleElement().asString().contains("never checks ModuleSwitch");
        assertThat(violations(ROOT + ".screens", "E.java",
                "@Scheduled(fixedRate = 1)\nvoid tick() { if (!ModuleSwitch.class.isInstance(x)) return; }\n", FIXTURE))
                .isEmpty();
        assertThat(violations(ROOT + ".domain.race", "F.java", "@EventListener\nvoid on(Foo f) {}\n", FIXTURE)).isEmpty();
    }
}
