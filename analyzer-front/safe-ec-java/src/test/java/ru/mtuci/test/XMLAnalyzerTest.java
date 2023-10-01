package ru.mtuci.test;

import io.churchkey.ec.Curve;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.mtuci.Utils;
import ru.mtuci.impl.props.XMLAnalyzer;

import java.util.Set;
import java.util.stream.Collectors;

public class XMLAnalyzerTest
{
    @Test
    public void testXmlConfig1()
    {
        var xmlAnalyzer = new XMLAnalyzer(TestUtils.getResourcePath("/test_data/config1.xml"));
        var requests = TestSafeEcClient.test(xmlAnalyzer::analyze);
        Assertions.assertEquals(2, requests.size());
        var expected = Set.of(Curve.secp256k1, Curve.idgostr34102001cryptoprocparamset);
        var actual = requests.stream().map(request -> Utils.curveByName(request.value())).collect(Collectors.toSet());
        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testXmlConfig2()
    {
        var xmlAnalyzer = new XMLAnalyzer(TestUtils.getResourcePath("/test_data/config2.xml"));
        var requests = TestSafeEcClient.test(xmlAnalyzer::analyze);
        Assertions.assertEquals(3, requests.size());
        Assertions.assertEquals(Curve.secp256r1, Utils.curveByName(requests.get(0).value()));
    }
}
