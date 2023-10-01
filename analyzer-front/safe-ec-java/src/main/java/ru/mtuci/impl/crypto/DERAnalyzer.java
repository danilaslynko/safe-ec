package ru.mtuci.impl.crypto;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.openssl.X509TrustedCertificateBlock;
import ru.mtuci.base.RequestingAnalyzer;
import ru.mtuci.net.Response;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;

@Slf4j
public class DERAnalyzer extends RequestingAnalyzer
{
    public static final List<String> MIME_TYPES = Arrays.asList("application/x-x509-ca-cert", "application/x-x509-user-cert", "application/pkix-cert");

    @FunctionalInterface
    private interface Reader<R>
    {
        R apply(byte[] val) throws Exception;
    }

    private static final List<Reader<?>> READERS = List.of(
            SubjectPublicKeyInfo::getInstance,
            PrivateKeyInfo::getInstance,
            X509TrustedCertificateBlock::new,
            Certificate::getInstance,
            ContentInfo::getInstance,
            ASN1ObjectIdentifier::getInstance,
            X9ECParameters::getInstance
    );

    public DERAnalyzer(Path path)
    {
        super(path);
    }

    @Override
    protected List<Future<Response>> makeFutureResponses()
    {
        var objects = tryParse();
        return ASN1Utils.makeRequests(objects, this::request);
    }

    private List<Object> tryParse()
    {
        List<ASN1Object> parsedObjects = new ArrayList<>();
        try (var inputStream = Files.newInputStream(path);
             var asn1InputStream = new ASN1InputStream(inputStream))
        {
            var asn1 = asn1InputStream.readObject();
            while (asn1 != null)
            {
                parsedObjects.add(asn1);
                asn1 = asn1InputStream.readObject();
            }
        }
        catch (Exception e)
        {
            log.error("Cannot parse DER file {}", path.toAbsolutePath());
            return Collections.emptyList();
        }

        return parsedObjects.stream().map(DERAnalyzer::read).filter(Objects::nonNull).toList();
    }

    private static Object read(ASN1Object obj)
    {
        byte[] bytes;
        try
        {
            bytes = obj.getEncoded();
        }
        catch (Exception e)
        {
            log.error("Skip, unable to serialize object {}", obj, e);
            return null;
        }

        for (var reader : READERS)
        {
            try
            {
                return reader.apply(bytes);
            }
            catch (Exception ignored)
            {
            }
        }

        log.debug("Skipped object {}", obj);
        return null;
    }
}
