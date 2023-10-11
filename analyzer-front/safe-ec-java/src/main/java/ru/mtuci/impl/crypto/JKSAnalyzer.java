package ru.mtuci.impl.crypto;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import ru.mtuci.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Map;

@Slf4j
public class JKSAnalyzer extends KeyStoreAnalyzer
{
    public static final List<String> EXTENSIONS = List.of(".jks");
    public static final List<String> MIME_TYPES = List.of("application/x-java-keystore");

    private final String password;
    private final String alias;
    private final String type;
    private final String provider;

    public JKSAnalyzer(Path path)
    {
        this(path, null, null, "JKS", null);
    }

    public JKSAnalyzer(Path path, String password, String alias, String type, String provider)
    {
        super(path, log);
        this.password = password;
        this.alias = alias;
        this.type = type;
        this.provider = provider;
    }

    @Override
    protected KeyStore getKeyStore()
    {
        var config = Config.INSTANCE.getAnalyzerConfig("jks");
        var password = this.password;
        if (StringUtils.isEmpty(password))
        {
            var keystores = (List<Map<String, String>>) config.get("keystores");
            password = keystores.stream()
                    .filter(keystore -> path.toString().equals(keystore.get("path")) ||
                                        path.getFileName().toString().equals(keystore.get("name")))
                    .findFirst()
                    .map(keystoreParams -> keystoreParams.get("password"))
                    .orElse("");
        }

        try (var is = Files.newInputStream(path))
        {
            var keystore = provider == null ? KeyStore.getInstance(type) : KeyStore.getInstance(type, provider);
            var passChars = password.toCharArray();
            keystore.load(is, passChars);
            return extractSingleAliasKeyStore(keystore, passChars, alias);
        }
        catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException | NoSuchProviderException e)
        {
            log.error("Cannot load keystore {}", path, e);
            return null;
        }
    }
}
