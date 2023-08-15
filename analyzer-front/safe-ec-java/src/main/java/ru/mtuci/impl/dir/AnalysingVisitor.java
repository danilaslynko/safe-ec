package ru.mtuci.impl.dir;

import ru.mtuci.AnalysisFailure;
import ru.mtuci.impl.Analyzer;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnalysingVisitor extends SimpleFileVisitor<Path> {
    private final List<AnalysisFailure> errors = new ArrayList<>();

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        Analyzer analyzer = Analyzer.getImpl(file);
        analyzer.analyze();
        this.errors.addAll(analyzer.getErrors());
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
        errors.add(new AnalysisFailure("Cannot visit path {}", file, exc));
        return FileVisitResult.CONTINUE;
    }

    public List<AnalysisFailure> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
