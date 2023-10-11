package ru.mtuci.swiftconnector;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import ru.mtuci.swiftconnector.service.crypto.KeyStoreLoader;
import ru.mtuci.swiftconnector.service.crypto.XmlSignService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SignVerifyTest
{
    @Test
    @SneakyThrows
    public void testSignVerify()
    {
        var cryptoConfig = new CryptoConfig()
                .setCertsDir(getTestResourcePath("certs"))
                .setKeyStore(getTestResourcePath("swift.jks"))
                .setKeyStorePassword("123456")
                .setKeyAlias("swift");
        var xmlSignService = new XmlSignService(cryptoConfig, new KeyStoreLoader());
        var xml = Files.readString(getTestResourcePath("test.xml"), StandardCharsets.UTF_8);
        xmlSignService.verifySignature(xmlSignService.signXml(xml));
    }

    @SneakyThrows
    private Path getTestResourcePath(String path)
    {
        if (!path.startsWith("/"))
            path = "/" + path;

        return Path.of(SignVerifyTest.class.getResource(path).toURI());
    }
}
