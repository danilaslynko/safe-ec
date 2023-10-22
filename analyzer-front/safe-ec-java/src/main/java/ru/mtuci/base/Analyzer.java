package ru.mtuci.base;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public abstract class Analyzer
{
    public static final Analyzer NOOP = new Analyzer(null)
    {
        @Override
        public void analyze()
        {
        }
    };

    protected final Path path;
    private final List<AnalysisFailure> errors = new ArrayList<>();

    @SneakyThrows
    protected Analyzer(Path path)
    {
        this.path = path == null ? null : path.toAbsolutePath().normalize();
        if (path != null)
            log.debug("Analyzer created for {}", this.path);
    }

    public abstract void analyze();

    public List<AnalysisFailure> getErrors()
    {
        return Collections.unmodifiableList(errors);
    }

    protected AnalysisFailure addError(String rule, String template, Object... args)
    {
        var error = new AnalysisFailure(rule, template, args);
        this.errors.add(error);
        return error;
    }

    protected void addErrors(List<AnalysisFailure> failures)
    {
        this.errors.addAll(failures);
    }
}
