package ru.mtuci.impl.crypto;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import ru.mtuci.Config;
import ru.mtuci.base.RequestingAnalyzer;
import ru.mtuci.net.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

@Slf4j
public class JKSAnalyzer extends RequestingAnalyzer
{
    public static final List<String> EXTENSIONS = List.of(".jks");
    public static final List<String> MIME_TYPES = List.of("application/x-java-keystore");

    public JKSAnalyzer(Path path)
    {
        super(path);
    }

    @Override
    protected List<Future<Response>> makeFutureResponses()
    {
        var config = Config.INSTANCE.getAnalyzerConfig("jks");
        var objects = new ArrayList<>();
        var keystores = (List<Map<String, String>>) config.get("keystores");
        var password = keystores.stream()
                .filter(keystore -> path.toString().equals(keystore.get("path")) ||
                                    path.getFileName().toString().equals(keystore.get("name")))
                .findFirst()
                .map(keystoreParams -> keystoreParams.get("password"))
                .orElse("");

        try (var is = Files.newInputStream(path))
        {
            var keystore = KeyStore.getInstance("JKS");
            keystore.load(is, password.toCharArray());
            var aliases = enumerationToList(keystore.aliases());
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
        catch (IOException e)
        {
            log.warn("Cannot open keystore {}", path, e);
            return Collections.emptyList();
        }
        catch (Exception e)
        {
            return ExceptionUtils.rethrow(e);
        }
    }

    private static <T> List<T> enumerationToList(Enumeration<T> enumeration)
    {
        var list = new ArrayList<T>();
        while (enumeration.hasMoreElements())
            list.add(enumeration.nextElement());

        return list;
    }
}
