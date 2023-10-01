package ru.mtuci.factory;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import ru.mtuci.base.Analyzer;
import ru.mtuci.impl.dir.JarAnalyzer;
import ru.mtuci.impl.clazz.ClassAnalyzer;
import ru.mtuci.impl.crypto.DERAnalyzer;
import ru.mtuci.impl.crypto.JKSAnalyzer;
import ru.mtuci.impl.crypto.PEMAnalyzer;
import ru.mtuci.impl.dir.DirAnalyzer;
import ru.mtuci.impl.props.PropertiesAnalyzer;
import ru.mtuci.impl.props.XMLAnalyzer;
import ru.mtuci.impl.props.YAMLAnalyzer;

import java.nio.file.Files;
import java.nio.file.Path;

import static ru.mtuci.base.Analyzer.NOOP;

@Slf4j
public class DefaultAnalyzerFactory implements AnalyzerFactory
{
    @Override
    @SneakyThrows
    public Analyzer getImpl(Path path)
    {
        if (Files.isDirectory(path))
            return new DirAnalyzer(path);

        if (Files.isSymbolicLink(path))
            path = path.toRealPath();

        String extension = null;
        int lastDot = path.toString().lastIndexOf('.');
        if (lastDot > -1)
            extension = path.toString().substring(lastDot);

        Analyzer analyzer = JarAnalyzer.EXTENSIONS.contains(extension) ? new JarAnalyzer(path)
                : ClassAnalyzer.EXTENSION.equals(extension) ? new ClassAnalyzer(path)
                : PropertiesAnalyzer.EXTENSIONS.contains(extension) ? new PropertiesAnalyzer(path)
                : XMLAnalyzer.EXTENSIONS.contains(extension) ? new XMLAnalyzer(path)
                : YAMLAnalyzer.EXTENSIONS.contains(extension) ? new XMLAnalyzer(path)
                : JKSAnalyzer.EXTENSIONS.contains(extension) ? new JKSAnalyzer(path)
                : NOOP;

        if (analyzer != NOOP)
            return analyzer;

        String mimeType = new Tika().detect(path);
        analyzer = JarAnalyzer.MIME_TYPES.contains(mimeType) ? new JarAnalyzer(path)
                : PropertiesAnalyzer.MIME_TYPES.contains(mimeType) ? new PropertiesAnalyzer(path)
                : DERAnalyzer.MIME_TYPES.contains(mimeType) ? new PropertiesAnalyzer(path)
                : PEMAnalyzer.MIME_TYPES.contains(mimeType) ? new PEMAnalyzer(path)
                : XMLAnalyzer.MIME_TYPES.contains(mimeType) ? new XMLAnalyzer(path)
                : YAMLAnalyzer.MIME_TYPES.contains(mimeType) ? new XMLAnalyzer(path)
                : JKSAnalyzer.MIME_TYPES.contains(mimeType) ? new JKSAnalyzer(path)
                : NOOP;

        if (analyzer == NOOP)
            log.trace("File {} skipped, extension {}, mime-type {}", path, extension, mimeType);

        return analyzer;
    }
}
