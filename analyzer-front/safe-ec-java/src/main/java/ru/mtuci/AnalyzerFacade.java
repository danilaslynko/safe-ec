package ru.mtuci;

import lombok.extern.slf4j.Slf4j;
import ru.mtuci.base.AnalysisFailure;
import ru.mtuci.factory.AnalyzerFactoryResolver;
import ru.mtuci.base.Analyzer;
import ru.mtuci.net.SafeEcClient;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class AnalyzerFacade
{
    public static Report analyze(Path path)
    {
        path = path.toAbsolutePath();
        Report report = new Report(path);
        if (!Files.exists(path))
        {
            log.error("Path {} does not exist, no analysis performed", path);
            report.add(new AnalysisFailure(null, "Path {} does not exist, no analysis performed", path));
            return report;
        }

        var safeEcClientConfig = Config.INSTANCE.getSafeEcClientConfig();
        Analyzer analyzer = AnalyzerFactoryResolver.resolveFactory().getImpl(path);
        SafeEcClient.withClient(analyzer::analyze, (String) safeEcClientConfig.get("host"), (Integer) safeEcClientConfig.get("port"));
        analyzer.getErrors().forEach(report::add);
        return report;
    }
}
