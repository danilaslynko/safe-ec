package ru.mtuci;

import com.contrastsecurity.sarif.Artifact;
import com.contrastsecurity.sarif.ArtifactLocation;
import com.contrastsecurity.sarif.MultiformatMessageString;
import com.contrastsecurity.sarif.Result;
import com.contrastsecurity.sarif.Run;
import com.contrastsecurity.sarif.SarifSchema210;
import com.contrastsecurity.sarif.Tool;
import com.contrastsecurity.sarif.ToolComponent;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import ru.mtuci.base.AnalysisFailure;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class Report
{
    private static final String DELIMITER = "--------------------------------------------------";
    private static final String TOOL_NAME = "Safe EC analyzer for Java programming language";

    private final List<AnalysisFailure> rows = new ArrayList<>();
    private final Path path;

    public Report add(AnalysisFailure failure)
    {
        if (failure != null)
            rows.add(failure);

        return this;
    }

    public boolean isOk()
    {
        return rows.isEmpty();
    }

    @SneakyThrows
    public String toSarifJsonString()
    {
        var versionInfo = VersionInfo.getInstance();
        return Utils.toJsonIndented(new SarifSchema210()
                .withVersion(SarifSchema210.Version._2_1_0)
                .with$schema(new URI("http://json.schemastore.org/sarif-2.1.0-rtm.4"))
                .withRuns(Collections.singletonList(new Run()
                        .withTool(new Tool().withDriver(
                                new ToolComponent()
                                        .withVersion(versionInfo.version())
                                        .withName("SafeEC")
                                        .withFullDescription(new MultiformatMessageString().withText(getToolDescription(versionInfo)))))
                        .withArtifacts(Collections.singleton(new Artifact().withLocation(new ArtifactLocation().withUri(this.path.toUri().toString()))))
                        .withResults(this.rows.stream().map(failure -> {
                            var result = new Result();
                            failure.accept(result);
                            return result;
                        }).collect(Collectors.toList()))
                )));
    }

    @Override
    @SneakyThrows
    public String toString()
    {
        List<AnalysisFailure> techErrors = new ArrayList<>();
        List<AnalysisFailure> normalFailures = new ArrayList<>();

        for (AnalysisFailure row : rows)
        {
            if (row.getCause() == null)
                normalFailures.add(row);
            else
                techErrors.add(row);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy hh:mm:ss");
        StringJoiner joiner = new StringJoiner(System.lineSeparator());
        joiner.add("""
                %s
                %s
                Analysis report for %s.
                %s
                                
                Summary:
                - %d total errors;
                - %d EC check errors;
                - %d unexpected errors;
                %s
                """.formatted(DELIMITER, formatter.format(LocalDateTime.now()), path, DELIMITER, rows.size(), normalFailures.size(), techErrors.size(), DELIMITER).stripIndent());

        if (!normalFailures.isEmpty())
        {
            joiner.add("EC check errors:");
            joiner.add("");
            for (AnalysisFailure failure : normalFailures)
                joiner.add(failure.getMessage());

            joiner.add(DELIMITER);
        }

        if (!techErrors.isEmpty())
        {
            joiner.add("Unexpected errors:");
            joiner.add("");
            for (AnalysisFailure techError : techErrors)
            {
                joiner.add(techError.getMessage());
                try (StringWriter sw = new StringWriter();
                     PrintWriter pw = new PrintWriter(sw))
                {
                    techError.getCause().printStackTrace(pw);
                    joiner.add(sw.toString());
                }
            }

            joiner.add(DELIMITER);
        }

        var versionInfo = VersionInfo.getInstance();
        joiner.add("Status: %s".formatted(isOk() ? "OK" : "FAILED"));
        joiner.add(DELIMITER);
        joiner.add(getToolDescription(versionInfo));

        return joiner.toString();
    }

    private static String getToolDescription(VersionInfo versionInfo)
    {
        return "%s (version %s, built by %s at %s)"
                .formatted(TOOL_NAME, versionInfo.version(), versionInfo.author(), versionInfo.buildDateTime());
    }

}
