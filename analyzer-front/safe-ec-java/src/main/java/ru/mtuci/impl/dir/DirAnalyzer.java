package ru.mtuci.impl.dir;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import ru.mtuci.base.Analyzer;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class DirAnalyzer extends Analyzer
{

    public DirAnalyzer(Path path)
    {
        super(path);
    }

    @Override
    @SneakyThrows
    public void analyze()
    {
        AnalysingVisitor visitor = new AnalysingVisitor();
        Files.walkFileTree(path, visitor);
        addErrors(visitor.getErrors());
    }
}
