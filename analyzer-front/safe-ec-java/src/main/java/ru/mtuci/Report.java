package ru.mtuci;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

@RequiredArgsConstructor
public class Report
{
    private static final String DELIMITER = "--------------------------------------------------";
    
    private final List<AnalysisFailure> rows = new ArrayList<>();
    private final Path path;
    
    public Report add(AnalysisFailure failure)
    {
        if (failure != null)
            rows.add(failure);
        
        return this;
    }
    
    public boolean isOk() {
        return rows.isEmpty();
    }
    
    @Override
    @SneakyThrows
    public String toString()
    {
        if (rows.isEmpty())
            return "";

        List<AnalysisFailure> techErrors = new ArrayList<>();
        List<AnalysisFailure> normalFailures = new ArrayList<>();
        
        for (AnalysisFailure row : rows) {
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
                """.formatted(DELIMITER, formatter.format(LocalDateTime.now()), DELIMITER, path, rows.size(), normalFailures.size(), techErrors.size(), DELIMITER).stripIndent());
        
        if (!normalFailures.isEmpty()) {
            joiner.add("EC check errors:");
            joiner.add("");
            for (AnalysisFailure failure : normalFailures)
                joiner.add(failure.getMessage());
            
            joiner.add(DELIMITER);
        }

        if (!techErrors.isEmpty()) {
            joiner.add("Unexpected errors:");
            joiner.add("");
            for (AnalysisFailure techError : techErrors) {
                joiner.add(techError.getMessage());
                try (StringWriter sw = new StringWriter();
                     PrintWriter pw = new PrintWriter(sw)) {
                    techError.getCause().printStackTrace(pw);
                    joiner.add(sw.toString());
                }
            }
            
            joiner.add(DELIMITER);
        }
        
        joiner.add("Status: %s".formatted(isOk() ? "OK" : "FAILED"));
        joiner.add(DELIMITER);
        joiner.add("Safe EC analyzer for Java programming language (version %s, built by %s at %s)".formatted());

        return joiner.toString();
    }

}
