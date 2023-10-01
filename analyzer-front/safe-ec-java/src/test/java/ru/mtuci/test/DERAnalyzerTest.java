package ru.mtuci.test;

import io.churchkey.asn1.Oid;
import io.churchkey.ec.Curve;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.mtuci.impl.crypto.DERAnalyzer;

public class DERAnalyzerTest
{
    @Test
    public void test()
    {
        var derAnalyzer = new DERAnalyzer(TestUtils.getResourcePath("/test_data/cert.cer"));
        var requests = TestSafeEcClient.test(derAnalyzer::analyze);
        Assertions.assertEquals(1, requests.size());
        Assertions.assertEquals(Curve.secp256r1, Curve.resolve(Oid.fromString(requests.get(0).value())));
    }
}
