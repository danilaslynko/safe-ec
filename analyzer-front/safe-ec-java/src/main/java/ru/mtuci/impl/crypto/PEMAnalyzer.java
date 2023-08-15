package ru.mtuci.impl.crypto;

import ru.mtuci.impl.Analyzer;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class PEMAnalyzer extends Analyzer {
    public static final String EXTENSION = ".pem";
    public static final List<String> MIME_TYPES = Arrays.asList("application/x-pem-file");
    
    public PEMAnalyzer(Path path) {
        super(path);
    }

    @Override
    public void analyze() {
        
    }
}
