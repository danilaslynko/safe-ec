package ru.mtuci.test;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.mtuci.impl.crypto.PEMAnalyzer;
import ru.mtuci.net.Request;

import java.nio.file.Path;
import java.util.List;

public class PEMAnalyzerTest
{
    @Test
    @SneakyThrows
    public void testCert()
    {
        Path cert = TestUtils.getResourcePath("/test_data/cert.pem");
        PEMAnalyzer pemAnalyzer = new PEMAnalyzer(cert);
        List<Request> requests = TestSafeEcClient.test(pemAnalyzer::analyze);
        Assertions.assertEquals(1, requests.size());
        Assertions.assertEquals(0, pemAnalyzer.getErrors().size());
    }

    @Test
    @SneakyThrows
    public void testCertChain()
    {
        Path cert = TestUtils.getResourcePath("/test_data/cert_chain.cer");
        PEMAnalyzer pemAnalyzer = new PEMAnalyzer(cert);
        List<Request> requests = TestSafeEcClient.test(pemAnalyzer::analyze);
        Assertions.assertEquals(0, requests.size()); // RSA certs only
    }
}
