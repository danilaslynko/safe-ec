package ru.mtuci.impl;

import lombok.SneakyThrows;
import ru.mtuci.impl.dir.DirAnalyzer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipAnalyzer extends Analyzer {
    public static final List<String> EXTENSIONS = Arrays.asList(".zip", ".jar", ".war", ".ear");
    public static final List<String> MIME_TYPES = Arrays.asList(
            "application/x-jar", "application/java-archive", "application/x-zip", "application/zip", "application/x-web-archive",
            "application/x-zip-compressed" // TODO дополнять по мере необходимости
    );
    
    public ZipAnalyzer(Path path) {
        super(path);
    }

    @Override
    @SneakyThrows
    public void analyze() {
        Path unpacked = null;
        try {
            unpacked = unpack();
            DirAnalyzer impl = new DirAnalyzer(unpacked);
            impl.analyze();
            addErrors(impl.getErrors());
        }
        finally {
            if (unpacked != null)
                Files.deleteIfExists(unpacked);
        }
    }
    
    @SneakyThrows
    private Path unpack() {
        Path destination = Paths.get(System.getProperty("java.io.tmpdir"), path.getFileName().toString() + "-" + System.nanoTime()).toAbsolutePath();
        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(Files.newInputStream(path));
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
            File newFile = newFile(destination.toString(), zipEntry);
            FileOutputStream fos = new FileOutputStream(newFile);
            int len;
            while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            zipEntry = zis.getNextEntry();
        }
        zis.closeEntry();
        zis.close();
        return destination;
    }

    @SneakyThrows
    private static File newFile(String destinationDir, ZipEntry zipEntry) {
        File destFile = new File(destinationDir, zipEntry.getName());
        String destDirPath = destinationDir.replace('\\', '/');
        String destFilePath = destFile.getCanonicalPath().replace('\\', '/');
        if (!destFilePath.startsWith(destDirPath + "/")) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }
        return destFile;
    }
}
