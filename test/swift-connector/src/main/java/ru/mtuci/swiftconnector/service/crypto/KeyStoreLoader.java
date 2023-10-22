package ru.mtuci.swiftconnector.service.crypto;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public final class KeyStoreLoader
{
    @SneakyThrows
    public synchronized KeyStore loadOrCreate(Path path, String password, String alias)
    {
        var keyStore = KeyStore.getInstance("JKS");
        var passChars = password.toCharArray();
        if (Files.exists(path))
        {
            try (var is = Files.newInputStream(path))
            {
                keyStore.load(is, passChars);
            }
        }
        else
        {
            keyStore.load(null, null);
        }

        if (!keyStore.containsAlias(alias))
        {
            var keyPairGenerator = KeyPairGenerator.getInstance("ECDSA", "BC");
            keyPairGenerator.initialize(new ECGenParameterSpec("secp224r1"));
            var keyPair = keyPairGenerator.generateKeyPair();

            var subject = new X500Name("CN=SafeEC,O=SWIFT");
            var serial = BigInteger.ONE; // serial number for self-signed does not matter a lot
            var notBefore = new Date();
            var notAfter = new Date(notBefore.getTime() + TimeUnit.DAYS.toMillis(365));

            var certificateBuilder = new JcaX509v3CertificateBuilder(
                    subject, serial,
                    notBefore, notAfter,
                    subject, keyPair.getPublic()
            );
            var certificateHolder = certificateBuilder.build(new JcaContentSignerBuilder("ECDSAWITHSHA1").build(keyPair.getPrivate()));
            var certificateConverter = new JcaX509CertificateConverter();
            certificateConverter.setProvider("BC");
            var certificate = certificateConverter.getCertificate(certificateHolder);

            keyStore.setKeyEntry(alias, keyPair.getPrivate(), passChars, new Certificate[]{certificate});
            try (var os = Files.newOutputStream(path))
            {
                keyStore.store(os, passChars);
            }
        }

        return keyStore;
    }
}
