package ru.mtuci.impl.crypto;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class CryptoProKeystoreAnalyzer extends KeyStoreAnalyzer
{
    @Getter
    private static volatile Boolean cryptoProLoaded = null;

    private static final String allCheckedIndicator = UUID.randomUUID().toString();
    private static final String globalKeyStore = UUID.randomUUID().toString();
    private static final Map<String, List<String>> checked = new ConcurrentHashMap<>();

    private final String password;
    private final String type;
    private final String alias;

    public CryptoProKeystoreAnalyzer(Path path, String type, String password, String alias)
    {
        super(path, log);
        this.password = password;
        this.type = type;
        this.alias = alias;
    }

    @Override
    protected KeyStore getKeyStore()
    {
        if (cryptoProLoaded == null)
        {
            try
            {
                Class<?> provider = Class.forName("ru.CryptoPro.JCP.JCP");
                if (Security.getProvider("JCP") == null)
                {
                    var prov = (Provider) provider.getConstructor().newInstance();
                    Security.addProvider(prov);
                }
                log.info("CryptoPro JCP loaded");
                cryptoProLoaded = true;
            }
            catch (ClassNotFoundException e)
            {
                log.warn("CryptoPro CSP is not loaded, keystore {} cannot be analyzed. To enable this feature, add CryptoPro JCSP libraries to SafeEC classpath", path);
                cryptoProLoaded = false;
            }
            catch (Exception e)
            {
                log.error("Unable to register CryptoPro JCP", e);
                cryptoProLoaded = false;
            }
        }

        if (!cryptoProLoaded)
            return null;

        var keyStoreId = path == null ? globalKeyStore : path.toString();
        var keyStoreIdForLogging = globalKeyStore.equals(keyStoreId) ? "GLOBAL" : keyStoreId;
        var checkedAliases = checked.computeIfAbsent(keyStoreId, __ -> new ArrayList<>());
        if (alias != null && checkedAliases.contains(alias) || checkedAliases.contains(allCheckedIndicator))
        {
            log.info("{} aliases in keystore {} already checked", alias == null ? "All" : "[" + alias + "]", keyStoreIdForLogging);
            return null;
        }


        try
        {
            var original = KeyStore.getInstance(type);
            var passChars = password == null ? null : password.toCharArray();
            if (path != null)
            {
                try (var is = Files.newInputStream(path))
                {
                    original.load(is, passChars);
                }
            }
            else
            {
                original.load(null, null);
            }

            checkedAliases.add(StringUtils.defaultString(alias, allCheckedIndicator));
            return extractSingleAliasKeyStore(original, alias);
        }
        catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e)
        {
            log.error("Cannot load CryptoPro keystore {}", keyStoreIdForLogging, e);
            return null;
        }
    }

}
