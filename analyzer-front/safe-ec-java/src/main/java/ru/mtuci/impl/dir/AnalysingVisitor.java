package ru.mtuci.impl.dir;

import lombok.RequiredArgsConstructor;
import ru.mtuci.base.AnalysisFailure;
import ru.mtuci.factory.AnalyzerFactoryResolver;
import ru.mtuci.base.Analyzer;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
public class AnalysingVisitor extends SimpleFileVisitor<Path>
{
    private final List<AnalysisFailure> errors = new ArrayList<>();

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
    {
        Analyzer analyzer = AnalyzerFactoryResolver.resolveFactory().getImpl(file);
        analyzer.analyze();
        this.errors.addAll(analyzer.getErrors());
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc)
    {
        errors.add(new AnalysisFailure("Cannot visit path {}", file, exc));
        return FileVisitResult.CONTINUE;
    }

    public List<AnalysisFailure> getErrors()
    {
        return Collections.unmodifiableList(errors);
    }
}
