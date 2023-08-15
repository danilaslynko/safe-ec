package ru.mtuci;

import lombok.extern.slf4j.Slf4j;
import ru.mtuci.impl.Analyzer;
import ru.mtuci.net.SafeEcClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class AnalyzerFacade {
    public static Report analyze(String path) {
        Path resolved = Paths.get(path).toAbsolutePath();
        Report report = new Report(resolved);
        if (!Files.exists(resolved)) {
            log.error("Path {} does not exist, no analysis performed", resolved);
            report.add(new AnalysisFailure("Path {} does not exist, no analysis performed", resolved));
            return report;
        }

        Analyzer analyzer = Analyzer.getImpl(resolved);
        try (var client = SafeEcClient.getInstance()) {
            analyzer.analyze();
        }
        analyzer.getErrors().forEach(report::add);
        return report;
    }
}
