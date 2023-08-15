package ru.mtuci;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AnalysisRunner {
    public static void printUsage(String[] args) {
        System.out.println("""
                Unknown args [%s].
                Usage: java <java options> -jar <jar name> /path/to/analyzed/file/.
                """.formatted(String.join(", ", args)).stripIndent());
    }
    
    public static void main(String[] args) {
        if (args.length != 1) {
            printUsage(args);
        }

        Report report = AnalyzerFacade.analyze(args[0]);
        log.info("Analysis completed{}{}", System.lineSeparator(), report);
        if (!report.isOk())
            System.exit(1);
    }
}
