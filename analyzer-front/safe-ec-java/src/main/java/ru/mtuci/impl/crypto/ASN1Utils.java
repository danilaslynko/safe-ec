package ru.mtuci.impl.crypto;

import io.churchkey.asn1.Oid;
import io.churchkey.ec.Curve;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.X509TrustedCertificateBlock;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import ru.mtuci.Utils;
import ru.mtuci.net.Request;
import ru.mtuci.net.Response;

import java.io.ByteArrayInputStream;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Consumer;

@Slf4j
public class ASN1Utils
{
    public static List<Future<Response>> makeRequests(Iterable<Object> objects, BiFunction<Request.Type, Object, Future<Response>> requestFunc)
    {
        JcaPEMKeyConverter keyConverter = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
        JcaX509CertificateConverter certConverter = new JcaX509CertificateConverter();
        List<Future<Response>> responses = new ArrayList<>();
        Consumer<Key> keyConsumer = key -> {
            if (!(key instanceof ECKey))
                return;

            String oid = null;
            if (key instanceof PublicKey)
            {
                SubjectPublicKeyInfo publicKey = SubjectPublicKeyInfo.getInstance(key.getEncoded());
                oid = ASN1ObjectIdentifier.getInstance(publicKey.getAlgorithm().getParameters()).toString();
            }
            else if (key instanceof PrivateKey)
            {
                PrivateKeyInfo privateKey = PrivateKeyInfo.getInstance(key.getEncoded());
                oid = ASN1ObjectIdentifier.getInstance(privateKey.getPrivateKeyAlgorithm().getParameters()).toString();
            }

            if (oid != null && Curve.resolve(Oid.fromString(oid)) != null)
                responses.add(requestFunc.apply(Request.Type.OID, oid));
        };

        for (Object parsedPem : objects)
        {
            try
            {
                if (parsedPem instanceof Certificate certificate)
                    keyConsumer.accept(certificate.getPublicKey());
                else if (parsedPem instanceof X509CertificateHolder holder)
                    keyConsumer.accept(certConverter.getCertificate(holder).getPublicKey());
                else if (parsedPem instanceof X509TrustedCertificateBlock trustedBlock)
                    keyConsumer.accept(certConverter.getCertificate(trustedBlock.getCertificateHolder()).getPublicKey());
                else if (parsedPem instanceof ContentInfo ci)
                    convertCertificates(Utils.getX509CertificateFactory().generateCertificates(new ByteArrayInputStream(ci.getEncoded()))).stream().map(Certificate::getPublicKey).forEach(keyConsumer);
                else if (parsedPem instanceof SubjectPublicKeyInfo publicKey)
                    keyConsumer.accept(keyConverter.getPublicKey(publicKey));
                else if (parsedPem instanceof PrivateKeyInfo privateKey)
                    keyConsumer.accept(keyConverter.getPrivateKey(privateKey));
                else if (parsedPem instanceof PEMKeyPair keyPair)
                    keyConsumer.accept(keyConverter.getPrivateKey(keyPair.getPrivateKeyInfo()));
                else if (parsedPem instanceof ASN1ObjectIdentifier oid && Curve.resolve(Oid.fromString(oid.toString())) != null)
                    responses.add(requestFunc.apply(Request.Type.OID, oid.toString()));
                else if (parsedPem instanceof X9ECParameters params)
                    responses.add(requestFunc.apply(Request.Type.Params, Request.Params.fromParameters(params)));
            }
            catch (Exception e)
            {
                log.warn("Unable to make request for check", e);
            }
        }
        return responses;
    }

    private static List<X509Certificate> convertCertificates(Collection<? extends Certificate> certs)
    {
        ArrayList<X509Certificate> convertedCerts = new ArrayList<>();

        if (certs == null)
            return convertedCerts;

        for (Certificate cert : certs)
            convertedCerts.add(convertCertificate(cert));

        return convertedCerts;
    }

    @SneakyThrows
    private static X509Certificate convertCertificate(Certificate certIn)
    {
        ByteArrayInputStream bais = new ByteArrayInputStream(certIn.getEncoded());
        return (X509Certificate) Utils.getX509CertificateFactory().generateCertificate(bais);
    }
}
