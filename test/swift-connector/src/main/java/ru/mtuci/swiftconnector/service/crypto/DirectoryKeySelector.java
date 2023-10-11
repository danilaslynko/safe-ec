package ru.mtuci.swiftconnector.service.crypto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.security.auth.x500.X500Principal;
import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.keyinfo.X509IssuerSerial;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public class DirectoryKeySelector extends KeySelector
{
    private static final Map<Pattern, String> ATTRIBUTE_MAPPINGS;

    static
    {
        Map<Pattern, String> mappings = new LinkedHashMap<>();
        mappings.put(Pattern.compile("^E="), "EMAILADDRESS=");
        mappings.put(Pattern.compile(",\\s*E="), ",EMAILADDRESS=");
        mappings.put(Pattern.compile("^S="), "ST=");
        mappings.put(Pattern.compile(",\\s*S="), ",ST=");

        ATTRIBUTE_MAPPINGS = Collections.unmodifiableMap(mappings);
    }

    private final CertsDirectory certsDirectory;

    @Getter
    private X509Certificate selected;

    @Override
    public KeySelectorResult select(KeyInfo keyInfo, KeySelector.Purpose purpose, AlgorithmMethod method, XMLCryptoContext context) throws KeySelectorException
    {
        for (XMLStructure info : keyInfo.getContent())
        {
            if (!(info instanceof X509Data x509Data))
                continue;

            for (Object o : x509Data.getContent())
            {
                if (o instanceof X509Certificate cert)
                {
                    cert = certsDirectory.get(cert);
                    var key = select(cert, method);
                    if (key != null)
                        return () -> key;
                }
                if (o instanceof X509IssuerSerial issuerSerial)
                {
                    var serial = issuerSerial.getSerialNumber().toString(16);
                    var issuerPrincipal = createPrincipal(issuerSerial.getIssuerName());
                    var cert = certsDirectory.get(serial, issuerPrincipal);
                    var key = select(cert, method);
                    if (key != null)
                        return () -> key;
                }
            }
        }

        throw new KeySelectorException("No key found");
    }

    private PublicKey select(X509Certificate cert, AlgorithmMethod method)
    {
        if (cert == null)
            return null;

        var key = cert.getPublicKey();
        if (!(method.getAlgorithm().equalsIgnoreCase(SignatureMethod.ECDSA_SHA256) && StringUtils.equalsAny(key.getAlgorithm(), "EC", "ECDSA")))
            return null;

        this.selected = cert;
        return key;
    }

    private X500Principal createPrincipal(String name)
    {
        String dn = name;
        for (Map.Entry<Pattern, String> entry : ATTRIBUTE_MAPPINGS.entrySet())
        {
            dn = entry.getKey().matcher(dn).replaceAll(entry.getValue());
        }
        return new X500Principal(dn);
    }
}
