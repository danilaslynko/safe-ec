package ru.mtuci.impl.props;

import io.churchkey.asn1.Oid;
import io.churchkey.ec.Curve;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import ru.mtuci.Utils;
import ru.mtuci.base.RequestingAnalyzer;
import ru.mtuci.impl.crypto.CryptoProKeystoreAnalyzer;
import ru.mtuci.impl.crypto.JKSAnalyzer;
import ru.mtuci.impl.crypto.PEMAnalyzer;
import ru.mtuci.net.Request;
import ru.mtuci.net.Response;
import ru.mtuci.utils.Either;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.regex.Pattern;

@Slf4j
public class PropertiesAnalyzer extends RequestingAnalyzer
{
    public static final List<String> EXTENSIONS = List.of(".properties");
    public static final List<String> MIME_TYPES = List.of("text/x-java-properties");

    private static final Pattern OID_PATTERN = Pattern.compile("^([0-2])((\\.0)|(\\.[1-9][0-9]*))*$");

    private static class SpringBootKeystoreProps
    {
        private static final String KEYSTORE_FILE_PROP = "server.ssl.key-store";
        private static final String KEYSTORE_TYPE_PROP = "server.ssl.key-store-type";
        private static final String KEYSTORE_PASS_PROP = "server.ssl.key-store-password";
        private static final String KEYSTORE_PROVIDER_PROP = "server.ssl.key-store-provider";
        private static final String KEY_ALIAS_PROP = "server.ssl.key-alias";

        private static final String TRUSTSTORE_TYPE_PROP = "server.ssl.trust-store";
        private static final String TRUSTSTORE_FILE_PROP = "server.ssl.trust-store-type";
        private static final String TRUSTSTORE_PASS_PROP = "server.ssl.trust-store-password";
        private static final String TRUSTSTORE_PROVIDER_PROP = "server.ssl.trust-store-provider";

        private static final String TRUST_CERT_PROP = "server.ssl.trust-certificate";
    }

    public PropertiesAnalyzer(Path path)
    {
        super(path);
    }

    @Override
    @SneakyThrows
    public List<RequestDto> makeRequests()
    {
        Properties props = new Properties();
        props.load(Files.newInputStream(path));
        return analyzeProps(props, m -> {
            var resp = request(m.toRequest());
            return Either.left(new RequestDto(resp, () -> this.baseMeta().property(m.prop()).build()));
        });
    }

    static List<RequestDto> analyzeProps(Properties props, Function<Matched, Either<RequestDto, Future<Response>>> request)
    {
        Properties toIterate = (Properties) props.clone();
        List<Matched> matches = new ArrayList<>();

        var requests = new ArrayList<RequestDto>();
        if (props.containsKey(SpringBootKeystoreProps.KEYSTORE_FILE_PROP))
        {
            String file = props.getProperty(SpringBootKeystoreProps.KEYSTORE_FILE_PROP);
            String type = props.getProperty(SpringBootKeystoreProps.KEYSTORE_TYPE_PROP, "JKS");
            String provider = props.getProperty(SpringBootKeystoreProps.KEYSTORE_PROVIDER_PROP, "SUN");
            String password = props.getProperty(SpringBootKeystoreProps.KEYSTORE_PASS_PROP, "");
            String alias = props.getProperty(SpringBootKeystoreProps.KEY_ALIAS_PROP);

            requests.addAll(analyzeKeyStore(file, type, password, provider, alias));
        }

        if (props.containsKey(SpringBootKeystoreProps.TRUSTSTORE_FILE_PROP))
        {
            String file = props.getProperty(SpringBootKeystoreProps.TRUSTSTORE_FILE_PROP);
            String type = props.getProperty(SpringBootKeystoreProps.TRUSTSTORE_TYPE_PROP, "JKS");
            String provider = props.getProperty(SpringBootKeystoreProps.TRUSTSTORE_PROVIDER_PROP, "SUN");
            String password = props.getProperty(SpringBootKeystoreProps.TRUSTSTORE_PASS_PROP, "");

            requests.addAll(analyzeKeyStore(file, type, password, provider, null));
        }

        if (props.containsKey(SpringBootKeystoreProps.TRUST_CERT_PROP))
        {
            var path = Path.of(props.getProperty(SpringBootKeystoreProps.TRUST_CERT_PROP));
            var pemAnalyzer = new PEMAnalyzer(path);
            requests.addAll(pemAnalyzer.makeRequests());
        }

        toIterate.forEach((keyObj, valueObj) -> {
            var prop = keyObj.toString();
            if (StringUtils.containsAnyIgnoreCase(prop, "exclud", "exclusion", "deprecat", "forbid"))
                return;

            List<Matched> matched = matchByNameOrOID(prop, valueObj.toString());
            if (!matched.isEmpty())
                matches.addAll(matched);
        });
        requests.addAll(matches.stream().map(m -> request.apply(m).fromLeft(RequestDto::of)).toList());
        return requests;
    }

    private static List<RequestDto> analyzeKeyStore(String file, String type, String password, String provider, String alias)
    {
        var path = Path.of(file);
        RequestingAnalyzer analyzer = null;
        if ("JKS".equals(type))
            analyzer = new JKSAnalyzer(path, password, alias, type, null);
        else if ("BKS".equals(type))
            analyzer = new JKSAnalyzer(path, password, alias, type, "BC");
        else if ("JCP".equals(provider))
            analyzer = new CryptoProKeystoreAnalyzer(path, type, password, alias);

        return analyzer != null ? analyzer.makeRequests() : Collections.emptyList();
    }

    static private List<Matched> matchByNameOrOID(String prop, String str)
    {
        List<Matched> result = new ArrayList<>();
        if (StringUtils.isEmpty(str))
            return result;

        for (String s : str.split("[,;|]"))
        {
            s = s.trim();
            if (OID_PATTERN.matcher(s).matches() && Curve.resolve(Oid.fromString(s)) != null)
                result.add(new Matched(prop, "OID", Collections.singletonMap("oid", s)));

            if (Utils.curveByName(s) != null)
                result.add(new Matched(prop, "Name", Collections.singletonMap("name", s)));
        }

        return result;
    }

    record Matched(String prop, String type, Map<String, String> properties)
    {
        Request toRequest()
        {
            Request.Type type = Request.Type.valueOf(this.type);
            Object data = switch (type)
            {
                case OID, Name -> properties.values().iterator().next();
                // TODO Действительно ли кто-то будет в пропертях передавать параметры кривой?
                case Params ->
                {
                    String form = properties.getOrDefault("form", "weierstrass");
                    yield switch (form)
                    {
                        case "weierstrass" -> fromWeierstrass(properties);
                        // TODO Конверсия из форм Монтгомери и Эдвардса
                        default -> throw new IllegalStateException("Unexpected value: " + form);
                    };
                }
            };
            return Request.of(type, data);
        }

        private static Request.Params fromWeierstrass(Map<String, String> properties)
        {
            int radix = "true".equalsIgnoreCase(properties.get("hex")) ? 16 : 10;
            BigInteger p = bi(properties.get("p"), radix);
            BigInteger a = bi(properties.get("weierstrass.a"), radix);
            BigInteger b = bi(properties.get("weierstrass.b"), radix);
            BigInteger x = bi(properties.get("base.x"), radix);
            BigInteger y = bi(properties.get("base.y"), radix);
            BigInteger n = bi(properties.get("n"), radix);
            BigInteger h = bi(properties.get("h"), radix);
            String seed = properties.get("seed");
            return new Request.Params("prime", p, a, b, x, y, n, h, seed);
        }

        private static BigInteger bi(String s, int radix)
        {
            return new BigInteger(s, radix);
        }
    }
}
