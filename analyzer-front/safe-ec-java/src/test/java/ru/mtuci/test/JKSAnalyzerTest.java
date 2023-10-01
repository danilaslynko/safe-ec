package ru.mtuci.test;

import io.churchkey.asn1.Oid;
import io.churchkey.ec.Curve;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.mtuci.impl.crypto.JKSAnalyzer;

public class JKSAnalyzerTest
{
    @Test
    @SneakyThrows
    public void test()
    {
        var jksAnalyzer = new JKSAnalyzer(TestUtils.getResourcePath("/test_data/test.jks"));
        var requests = TestSafeEcClient.test(jksAnalyzer::analyze);
        Assertions.assertEquals(1, requests.size());
        Assertions.assertEquals(Curve.secp256r1, Curve.resolve(Oid.fromString(requests.get(0).value())));
    }
}
