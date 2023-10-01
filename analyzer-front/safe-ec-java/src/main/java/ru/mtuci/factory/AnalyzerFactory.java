package ru.mtuci.factory;

import ru.mtuci.base.Analyzer;

import java.nio.file.Path;

public interface AnalyzerFactory
{
    Analyzer getImpl(Path path);
}
