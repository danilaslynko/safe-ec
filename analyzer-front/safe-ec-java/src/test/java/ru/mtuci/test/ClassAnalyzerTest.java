package ru.mtuci.test;

import com.contrastsecurity.sarif.Result;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mtuci.base.Analyzer;
import ru.mtuci.impl.clazz.ClassAnalyzer;
import ru.mtuci.impl.crypto.CryptoProKeystoreAnalyzer;
import ru.mtuci.net.Request;
import ru.mtuci.net.Response;

import java.nio.file.Path;

public class ClassAnalyzerTest
{
    @BeforeEach
    public void resetCounter()
    {
        Request.resetCounter();
    }

    @Test
    @SneakyThrows
    public void testClassAnalyzer()
    {
        Path path = getClassForTestsPath();
        Analyzer analyzer = new ClassAnalyzer(path);
        TestSafeEcClient.addAnswer("1", new Response("1", Response.Type.VULNERABLE, null, "Vulnerable curve 1"));
        TestSafeEcClient.addAnswer("2", new Response("2", Response.Type.VULNERABLE, null, "Vulnerable curve 2"));
        var requests = TestSafeEcClient.test(analyzer::analyze);
        if (CryptoProKeystoreAnalyzer.getCryptoProLoaded())
        {
            var req = requests.get(3);
            Assertions.assertEquals(Request.Type.OID, req.type());
            Assertions.assertEquals("1.2.643.2.2.35.1", req.value());
        }
        var failures = analyzer.getErrors();
        Assertions.assertEquals(2, failures.size());
        var result = new Result();
        failures.get(0).getMeta().accept(result);
        Assertions.assertEquals(1, result.getLocations().size());
    }

    static Path getClassForTestsPath()
    {
        return TestUtils.getResourcePath("/" + TestClass.class.getName().replace(".", "/") + ".class");
    }
}
