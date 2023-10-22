package ru.mtuci.test;

import com.contrastsecurity.sarif.Result;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mtuci.impl.dir.JarAnalyzer;
import ru.mtuci.net.Request;
import ru.mtuci.net.Response;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class JarAnalyzerTest
{
    private Path jarPath;
    private Path expectedClassPath;

    @BeforeEach
    @SneakyThrows
    public void createJar()
    {
        var bytes = Files.readAllBytes(ClassAnalyzerTest.getClassForTestsPath());
        var entryName = "TestClass.class";
        var entries = new ArrayList<String>();
        entries.add(entryName);
        var dirForJar = Paths.get("").toAbsolutePath();
        expectedClassPath = dirForJar;
        for (int i = 0; i < 3; i++)
        {
            var jarName = "test-jar-" + i + ".jar";
            entries.add(jarName);
            jarPath = dirForJar.resolve(jarName);
            try (var out = new ZipOutputStream(Files.newOutputStream(jarPath)))
            {
                var e = new ZipEntry(entryName);
                out.putNextEntry(e);
                out.write(bytes, 0, bytes.length);
                out.closeEntry();
            }
            if (i != 2)
            {
                entryName = jarPath.getFileName().toString();
                bytes = Files.readAllBytes(jarPath);
                Files.deleteIfExists(jarPath);
            }
        }
        Collections.reverse(entries);
        entries.forEach(entry -> expectedClassPath = expectedClassPath.resolve(entry));
        Request.resetCounter();
    }

    @AfterEach
    @SneakyThrows
    public void deleteJar()
    {
        Files.deleteIfExists(jarPath);
        jarPath = null;
        expectedClassPath = null;
    }

    @Test
    public void test()
    {
        var jarAnalyzer = new JarAnalyzer(jarPath);
        TestSafeEcClient.addAnswer("1", new Response("1", Response.Type.VULNERABLE, null, "Vulnerable curve 1"));
        TestSafeEcClient.addAnswer("2", new Response("2", Response.Type.VULNERABLE, null, "Vulnerable curve 2"));
        TestSafeEcClient.test(jarAnalyzer::analyze);
        var failures = jarAnalyzer.getErrors();
        var result = new Result();
        failures.get(0).getMeta().accept(result);
        var location = result.getLocations().get(0);
        Assertions.assertEquals(expectedClassPath.toAbsolutePath().toUri().toString(), location.getPhysicalLocation().getArtifactLocation().getUri());
    }
}
