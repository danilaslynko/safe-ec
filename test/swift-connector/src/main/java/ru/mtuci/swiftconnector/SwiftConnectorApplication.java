package ru.mtuci.swiftconnector;

import com.objsys.asn1j.runtime.Asn1BerEncodeBuffer;
import com.objsys.asn1j.runtime.Asn1IA5String;
import com.objsys.asn1j.runtime.Asn1ObjectIdentifier;
import com.objsys.asn1j.runtime.Asn1OctetString;
import lombok.SneakyThrows;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import ru.CryptoPro.Crypto.CryptoProvider;
import ru.CryptoPro.JCP.ASN.CertificateExtensions.GeneralName;
import ru.CryptoPro.JCP.ASN.CertificateExtensions.GeneralNames;
import ru.CryptoPro.JCP.ASN.PKIX1Explicit88.Extension;
import ru.CryptoPro.JCP.JCP;
import ru.CryptoPro.JCPRequest.GostCertificateRequest;
import ru.CryptoPro.reprov.RevCheck;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Arrays;

@SpringBootApplication(scanBasePackages = "ru.mtuci.swiftconnector")
public class SwiftConnectorApplication
{
    @SneakyThrows
    public static void main(String[] args)
    {
        Security.addProvider(new BouncyCastleProvider());
        Security.addProvider(new JCP());
        Security.addProvider(new RevCheck());
        Security.addProvider(new CryptoProvider());


//        KeyStore hdImageStore = KeyStore.getInstance("HDImageStore");
//        hdImageStore.load(Files.newInputStream(Path.of("/home/danya/messages/safe-ec/key.store")), "123456".toCharArray());
//        var certs = hdImageStore.getCertificateChain("safeEc");
//        System.out.println(Arrays.toString(certs));
//        try (var os = Files.newOutputStream(Path.of("/home/danya/messages/safe-ec/key.store")))
//        {
//            hdImageStore.store(os, "123456".toCharArray());
//        }
//        var key = hdImageStore.getKey("safeEc", "123456".toCharArray());
//        var certificate = hdImageStore.getCertificate("safeEc");
//        String certName = "CN=SafeEC,O=MTUCI,OU=KIIB,C=RU";
//        String httpAddress = "http://www.cryptopro.ru/certsrv/";
// создание запроса на сертификат аутентификации сервера
//        GostCertificateRequest request = new GostCertificateRequest();
//        request.setKeyUsage(GostCertificateRequest.CRYPT_DEFAULT);
//        request.addExtKeyUsage(GostCertificateRequest.INTS_PKIX_CLIENT_AUTH);
//        request.addExtKeyUsage(GostCertificateRequest.INTS_PKIX_SERVER_AUTH);
//        var localhost = new GeneralNames(new GeneralName[]{new GeneralName((byte) 3, new Asn1IA5String("localhost"))});
//        var buffer = new Asn1BerEncodeBuffer();
//        localhost.encode(buffer, true);
//
//        var bytes = buffer.getByteArrayInputStream().readAllBytes();
//        request.addExtension(new Extension(new Asn1ObjectIdentifier(new int[]{2,5,29,17}), new Asn1OctetString(bytes)));
//        request.setPublicKeyInfo(certificate.getPublicKey());
//        request.setSubjectInfo(certName);
//        request.encodeAndSign((PrivateKey) key);
// отправка запроса центру сертификации и получение от центра
// сертификата в DER-кодировке
//        byte[] encoded = request.getEncodedCert(httpAddress);
//        request.printToBASE64(new PrintStream(Files.newOutputStream(Path.of("/home/danya/deployment/gost.csr"))));
// генерация X509-сертификата из закодированного представления сертификата
//        CertificateFactory cf = CertificateFactory.getInstance("X509");
//        java.security.cert.Certificate cert =
//                cf.generateCertificate(new ByteArrayInputStream(encoded));
////        System.out.println(cert);
//
//        FileInputStream fis = new FileInputStream("/home/danya/Загрузки/ca.cer");
//        java.security.cert.Certificate certRoot =
//                cf.generateCertificate(new BufferedInputStream(fis));
//        java.security.cert.Certificate[] certs = new java.security.cert.Certificate[2];
//        certs[0] = certRoot;
//        certs[1] = cert;
//        hdImageStore.setKeyEntry("safeEc", key, "123456".toCharArray(), certs);
//        hdImageStore.store(Files.newOutputStream(Path.of("/home/danya/messages/safe-ec/key.store")), "123456".toCharArray());
//
        SpringApplication.run(SwiftConnectorApplication.class, args);
    }
}
