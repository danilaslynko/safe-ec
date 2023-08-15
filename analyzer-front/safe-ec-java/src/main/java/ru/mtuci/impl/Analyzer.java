package ru.mtuci.impl;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import ru.mtuci.AnalysisFailure;
import ru.mtuci.impl.clazz.ClassAnalyzer;
import ru.mtuci.impl.crypto.DERAnalyzer;
import ru.mtuci.impl.crypto.JKSAnalyzer;
import ru.mtuci.impl.crypto.PEMAnalyzer;
import ru.mtuci.impl.dir.DirAnalyzer;
import ru.mtuci.impl.props.PropertiesAnalyzer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public abstract class Analyzer {
    private static final Analyzer NOOP = new Analyzer(null) {
        @Override
        public void analyze() {}
    };
    
    protected final Path path;
    private final List<AnalysisFailure> errors = new ArrayList<>();

    protected Analyzer(Path path) {
        this.path = path;
    }

    public abstract void analyze();
    
    public List<AnalysisFailure> getErrors() {
        return Collections.unmodifiableList(errors);
    }
    
    protected void addError(String template, Object... args) {
        this.errors.add(new AnalysisFailure(template, args));
    }
    
    protected void addErrors(List<AnalysisFailure> failures) {
        this.errors.addAll(failures);
    }

    @SneakyThrows
    public static Analyzer getImpl(Path path) {
        if (Files.isDirectory(path))
            return new DirAnalyzer(path);
        
        if (Files.isSymbolicLink(path))
            path = path.toRealPath();

        String extension = null;
        int lastDot = path.toString().lastIndexOf('.');
        if (lastDot > -1)
            extension = path.toString().substring(lastDot);
        
        Analyzer analyzer = ZipAnalyzer.EXTENSIONS.contains(extension) ? new ZipAnalyzer(path)
                : ClassAnalyzer.EXTENSION.equals(extension) ? new ClassAnalyzer(path)
                : PropertiesAnalyzer.EXTENSION.equals(extension) ? new PropertiesAnalyzer(path)
                : DERAnalyzer.EXTENSION.equals(extension) ? new DERAnalyzer(path)
                : JKSAnalyzer.EXTENSION.equals(extension) ? new JKSAnalyzer(path)
                : PEMAnalyzer.EXTENSION.equals(extension) ? new PEMAnalyzer(path)
                : NOOP;
        
        if (analyzer != NOOP)
            return analyzer;
        
        String mimeType = new Tika().detect(path);
        analyzer = ZipAnalyzer.MIME_TYPES.contains(mimeType) ? new ZipAnalyzer(path)
                : PropertiesAnalyzer.MIME_TYPES.contains(mimeType) ? new PropertiesAnalyzer(path)
                : DERAnalyzer.MIME_TYPES.contains(mimeType) ? new PropertiesAnalyzer(path)
                : PEMAnalyzer.MIME_TYPES.contains(mimeType) ? new PEMAnalyzer(path)
                : NOOP;
        
        if (analyzer == NOOP)
            log.trace("File {} skipped, extension {}, mime-type {}", path, extension, mimeType);
        
        return analyzer;
    }
}
