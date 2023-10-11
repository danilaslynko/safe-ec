package ru.mtuci.swiftconnector.service.crypto;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class CertsDirectory
{
    private final Map<Pair<String, X500Principal>, X509Certificate> certs;

    @SneakyThrows
    public CertsDirectory(Path certsDir)
    {
        var certs = new ArrayList<X509Certificate>();
        try (var stream = Files.list(certsDir))
        {
            var list = stream.filter(Files::isRegularFile).toList();
            var added = 0;
            for (Path certFile : list)
            {
                try
                {
                    var fileCerts = loadFile(certFile);
                    var iterator = fileCerts.iterator();
                    while (iterator.hasNext())
                    {
                        var cert = iterator.next();
                        try
                        {
                            var x500name = new JcaX509CertificateHolder(cert).getSubject();
                            try
                            {
                                cert.checkValidity();
                                added++;
                                continue;
                            }
                            catch (CertificateNotYetValidException e)
                            {
                                log.info("Certificate is not valid yet [{}], valid from [{}]", x500name, cert.getNotBefore().toString());
                            }
                            catch (CertificateExpiredException e)
                            {
                                log.debug("Certificate is expired [{}], was valid until [{}]", x500name, cert.getNotAfter().toString());
                            }
                        }
                        catch (CertificateEncodingException e)
                        {
                            log.error(e.getMessage(), e);
                        }
                        iterator.remove();
                    }
                    certs.addAll(fileCerts);
                }
                catch (Exception e)
                {
                    log.error("Unable to load certificates from file {}", certFile.toAbsolutePath());
                }
            }
            log.info("Loaded {} certificates from {}", added, certsDir.toAbsolutePath());
            this.certs = certs.stream().collect(Collectors.toMap(CertsDirectory::getCertKey, Function.identity()));
        }
    }


    @SneakyThrows
    public static List<X509Certificate> loadFile(Path fileEntry)
    {
        var bytes = Files.readAllBytes(fileEntry);
        if (bytes.length <= 2)
            return new ArrayList<>();

        if (bytes[0] == 0x30 && bytes[1] == (byte) 0x82)
        {
            return loadCertFromDER(bytes);
        }
        else
        {
            return loadCertsFromPEM(new String(bytes, StandardCharsets.UTF_8), fileEntry);
        }
    }

    public static List<X509Certificate> loadCertFromDER(byte[] bytes)
            throws CertificateException, NoSuchProviderException
    {
        var is = new ByteArrayInputStream(bytes);
        var cert = (X509Certificate)
                java.security.cert.CertificateFactory
                        .getInstance("X509", "BC")
                        .generateCertificate(is);
        var certs = new ArrayList<X509Certificate>();
        certs.add(cert);
        return certs;
    }

    public static List<X509Certificate> loadCertsFromPEM(String s, Path path)
    {
        var reader = new StringReader(s);
        var pem = new PEMParser(reader);
        var certs = new ArrayList<X509Certificate>();
        Object read = null;
        do
        {
            try
            {
                read = pem.readObject();
                if (read instanceof X509Certificate cert)
                    certs.add(cert);
                else if (read instanceof X509CertificateHolder holder)
                    certs.add(new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(holder));
            }
            catch (CertificateException | IOException e)
            {
                log.error("Error during parsing PEM file [" + path.toAbsolutePath() + "]: " + e.getMessage(), e);
            }

        } while (read != null);

        return certs;
    }

    public X509Certificate get(X509Certificate cert)
    {
        return certs.get(getCertKey(cert));
    }

    public X509Certificate get(String serial, X500Principal issuer)
    {
        return certs.get(Pair.of(serial, issuer));
    }

    public static Pair<String/* serial in hex format */, X500Principal> getCertKey(X509Certificate cert)
    {
        var serial = cert.getSerialNumber().toString(16);
        return Pair.of(serial, cert.getIssuerX500Principal());
    }
}
