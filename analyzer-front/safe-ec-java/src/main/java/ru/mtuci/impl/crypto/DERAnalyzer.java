package ru.mtuci.impl.crypto;

import ru.mtuci.impl.Analyzer;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class DERAnalyzer extends Analyzer {
    public static final String EXTENSION = ".der";
    public static final List<String> MIME_TYPES = Arrays.asList("application/x-x509-ca-cert", "application/x-x509-user-cert", "application/pkix-cert");

    public DERAnalyzer(Path path) {
        super(path);
    }

    @Override
    public void analyze() {
        
    }
}
