package ru.mtuci;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionHandlerFilter;
import org.kohsuke.args4j.spi.PathOptionHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Slf4j
public class AnalysisRunner
{
    @Option(name = "--sarif-json", handler = PathOptionHandler.class, aliases = "-sj", usage = "export results in SARIF format to specified file")
    private Path sarifJsonFile;

    @Argument(handler = PathOptionHandler.class, required = true)
    private Path target;

    public static void main(String[] args)
    {
        new AnalysisRunner(args).doMain();
    }

    @SneakyThrows
    private AnalysisRunner(String[] args)
    {
        var parser = new CmdLineParser(this);
        try
        {
            parser.parseArgument(args);
        }
        catch (CmdLineException e)
        {
            System.err.println(e.getMessage());
            System.err.println("java -jar safe-ec.jar [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();
            System.err.println("  Example: java SampleMain" + parser.printExample(OptionHandlerFilter.ALL));
            System.exit(64);
        }
    }

    public void doMain()
    {
        Report report = AnalyzerFacade.analyze(target);
        exportSarif(report);
        log.info("Analysis completed{}{}", System.lineSeparator(), report);
        if (!report.isOk())
            System.exit(1);
    }

    private void exportSarif(Report report)
    {
        if (this.sarifJsonFile == null)
            return;

        try
        {
            Files.writeString(sarifJsonFile, report.toSarifJsonString(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        catch (Exception e)
        {
            log.error("Unable to write SARIF results", e);
        }
    }
}
