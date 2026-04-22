package org.paul;

import org.paul.config.DecompileConfig;
import org.paul.filter.ClassFilter;
import org.paul.formatter.UmlFormatter;
import org.paul.introspection.ClassInspector;
import org.paul.loader.JarLoader;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(
        name = "java-decompiler",
        description = "Generate UML diagrams from a JAR file.",
        mixinStandardHelpOptions = true
)
public class Main implements Callable<Integer> {

    @Option(names = "--ignore", paramLabel = "PATTERN", split = ",",
            description = "Class name patterns to exclude, comma-separated or repeated (e.g. 'java.lang.*').")
    private List<String> ignorePatterns = List.of();
    @Parameters(index = "0", paramLabel = "JAR", description = "Path to the JAR file to analyse.")
    private String jarPath;
    @Option(names = "--format", required = true,
            description = "Output format: ${COMPLETION-CANDIDATES}.")
    private String format;
    @Option(names = "--output", paramLabel = "FILE",
            description = "Write output to FILE instead of stdout.")
    private Path outputFile;

    static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    /**
     * Runs the full pipeline: load → filter → inspect → format.
     */
    public static String decompile(String jarPath, UmlFormatter formatter, DecompileConfig config) {
        List<Class<?>> filtered = ClassFilter.filter(JarLoader.load(jarPath), config);

        Set<String> loadedNames = filtered.stream()
                .map(c -> config.fullyQualified() ? c.getName() : c.getSimpleName())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return formatter.format(
                filtered.stream()
                        .map(c -> ClassInspector.inspect(c, config, loadedNames))
                        .toList(),
                config);
    }

    @Override
    public Integer call()
     {
        // Open formatter registry: --format <Name> resolves to org.paul.formatter.<Name>Formatter.
        // To add a new formatter, create a class implementing UmlFormatter in that package.
        // No changes to Main are required.
        UmlFormatter formatter;
        try {
            String formatName = format.substring(0, 1).toUpperCase() + format.substring(1);
            formatter = (UmlFormatter) Class.forName("org.paul.formatter." + formatName + "Formatter")
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Unknown formatter: " + format, e);
        }
        DecompileConfig config = new DecompileConfig(ignorePatterns, false, true, true);
        String result = decompile(jarPath, formatter, config);

        if (outputFile != null) {
            try {
                Files.writeString(outputFile, result);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            System.out.print(result);
        }

        return 0;
    }
}
