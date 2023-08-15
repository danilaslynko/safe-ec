package ru.mtuci.impl.crypto;

import ru.mtuci.impl.Analyzer;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class JKSAnalyzer extends Analyzer {
    public static final String EXTENSION = ".jks";
    
    public JKSAnalyzer(Path path) {
        super(path);
    }

    @Override
    public void analyze() {
        
    }
}
