package ru.mtuci;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import ru.mtuci.impl.clazz.ClassAnalyzer;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ClassAnalyzerTest {
    @Test
    @SneakyThrows
    public void testClassAnalyzer() {
        URL resource = ClassAnalyzerTest.class.getResource("/BatchBpmHandler.class");
        Path path = Paths.get(resource.toURI());
        ClassAnalyzer analyzer = new ClassAnalyzer(path);
        analyzer.analyze();
    }
}
