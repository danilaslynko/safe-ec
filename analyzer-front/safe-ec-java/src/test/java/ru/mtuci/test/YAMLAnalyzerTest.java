package ru.mtuci.test;

import io.churchkey.ec.Curve;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.mtuci.Utils;
import ru.mtuci.impl.props.YAMLAnalyzer;

public class YAMLAnalyzerTest
{
    @Test
    public void testYamlConfig()
    {
        var yamlAnalyzer = new YAMLAnalyzer(TestUtils.getResourcePath("/test_data/config.yaml"));
        var requests = TestSafeEcClient.test(yamlAnalyzer::analyze);
        Assertions.assertEquals(2, requests.size());
        var request = requests.get(0);
        Assertions.assertEquals(Curve.secp256k1, Utils.curveByName(request.value()));
    }
}
