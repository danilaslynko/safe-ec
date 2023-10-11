package ru.mtuci.impl.crypto;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import ru.mtuci.base.RequestingAnalyzer;
import ru.mtuci.net.Response;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

public abstract class KeyStoreAnalyzer extends RequestingAnalyzer
{
    private final Logger log;

    protected KeyStoreAnalyzer(Path path, Logger log)
    {
        super(path);
        this.log = log;
    }

    @Override
    public List<Future<Response>> makeFutureResponses()
    {
        var objects = new ArrayList<>();
        try
        {
            var keystore = getKeyStore();
            if (keystore == null)
            {
                log.warn("Keystore {} skipped", path);
                return Collections.emptyList();
            }

            var aliases = Collections.list(keystore.aliases());
            for (String alias : aliases)
            {
                if (keystore.isCertificateEntry(alias))
                {
                    var certificate = keystore.getCertificate(alias);
                    if (certificate != null)
                        objects.add(certificate);
                }
                else if (keystore.isKeyEntry(alias))
                {
                    var chain = keystore.getCertificateChain(alias);
                    if (chain != null)
                        objects.addAll(Arrays.asList(chain));
                }
            }
            return ASN1Utils.makeRequests(objects, this::request);
        }
        catch (Exception e)
        {
            return ExceptionUtils.rethrow(e);
        }
    }

    protected abstract KeyStore getKeyStore();

    protected static KeyStore extractSingleAliasKeyStore(KeyStore original, String alias)
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException
    {
        var tempOs = new ByteArrayOutputStream();
        original.store(tempOs, "123456".toCharArray());

        var storeBytes = tempOs.toByteArray();
        var tempIs = new ByteArrayInputStream(storeBytes);

        var forAnalysis = KeyStore.getInstance(original.getType());
        forAnalysis.load(tempIs, "123456".toCharArray());
        var aliases = Collections.list(forAnalysis.aliases());
        if (StringUtils.isNotEmpty(alias) && aliases.contains(alias))
        {
            aliases.stream().filter(a -> !alias.equals(a)).forEach(a -> {
                try
                {
                    forAnalysis.deleteEntry(a);
                }
                catch (KeyStoreException e)
                {
                    ExceptionUtils.rethrow(e);
                }
            });
        }
        return forAnalysis;
    }
}
