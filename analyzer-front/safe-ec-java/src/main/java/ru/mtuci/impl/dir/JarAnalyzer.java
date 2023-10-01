package ru.mtuci.impl.dir;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import ru.mtuci.Config;
import ru.mtuci.base.Analyzer;
import ru.mtuci.factory.AnalyzerFactoryResolver;
import ru.mtuci.utils.VirtualPaths;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
public class JarAnalyzer extends Analyzer
{
    private static final AtomicInteger fileCounter = new AtomicInteger(1);

    public static final List<String> EXTENSIONS = Arrays.asList(".zip", ".jar", ".war", ".ear");
    public static final List<String> MIME_TYPES = Arrays.asList(
            "application/x-jar", "application/java-archive", "application/x-zip", "application/zip", "application/x-web-archive",
            "application/x-zip-compressed" // TODO дополнять по мере необходимости
    );

    public JarAnalyzer(Path path)
    {
        super(path);
    }

    @Override
    @SneakyThrows
    public void analyze()
    {
        var config = Config.INSTANCE.getAnalyzerConfig("jar");
        var excluded = config == null ? null : (List<String>) config.get("excluded");
        if (excluded != null && excluded.contains(path.getFileName().toString()))
        {
            log.debug("File {} was ignored", path);
            return;
        }

        Path unpacked = null;
        try
        {
            unpacked = unpack();
            var impl = AnalyzerFactoryResolver.resolveFactory().getImpl(unpacked);
            impl.analyze();
            addErrors(impl.getErrors());
        }
        finally
        {
            if (unpacked != null)
                deleteFolder(unpacked);
        }
    }

    @SneakyThrows
    private static void deleteFolder(Path unpacked)
    {
        try (var walk = Files.walk(unpacked))
        {
            walk.sorted(Comparator.reverseOrder()).forEach(f -> {
                try
                {
                    Files.deleteIfExists(f);
                }
                catch (IOException e)
                {
                    ExceptionUtils.rethrow(e);
                }
            });
        }
    }

    @SneakyThrows
    private Path unpack()
    {
        Path destination = Paths.get(System.getProperty("java.io.tmpdir"), path.getFileName().toString() + "-unpacked-" + fileCounter.getAndIncrement()).toAbsolutePath();
        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(path)))
        {
            for (ZipEntry zipEntry = zis.getNextEntry(); zipEntry != null; zipEntry = zis.getNextEntry())
            {
                if (zipEntry.isDirectory())
                    continue;

                Path newFile = newFile(destination.toString(), zipEntry);
                Files.createDirectories(newFile.getParent());
                try (var out = new BufferedOutputStream(Files.newOutputStream(newFile)))
                {
                    int len;
                    while ((len = zis.read(buffer)) > 0)
                        out.write(buffer, 0, len);
                }
                zis.closeEntry();
            }
        }
        VirtualPaths.mapVirtualToReal(destination, path);
        return destination;
    }

    @SneakyThrows
    private static Path newFile(String destinationDir, ZipEntry zipEntry)
    {
        var destFile = Paths.get(destinationDir, zipEntry.getName());
        String destDirPath = destinationDir.replace('\\', '/');
        String destFilePath = destFile.toAbsolutePath().toString().replace('\\', '/');
        if (!destFilePath.startsWith(destDirPath + "/"))
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());

        return destFile;
    }
}
