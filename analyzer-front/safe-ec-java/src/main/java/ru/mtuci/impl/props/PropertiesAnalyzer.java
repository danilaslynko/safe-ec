package ru.mtuci.impl.props;

import io.churchkey.asn1.Oid;
import io.churchkey.ec.Curve;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import ru.mtuci.Utils;
import ru.mtuci.base.RequestingAnalyzer;
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

    public PropertiesAnalyzer(Path path)
    {
        super(path);
    }

    @Override
    @SneakyThrows
    protected List<RequestDto> makeRequests()
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
        toIterate.forEach((keyObj, valueObj) -> {
            var prop = keyObj.toString();
            if (StringUtils.containsAnyIgnoreCase(prop, "exclud", "exclusion", "deprecat", "forbid"))
                return;

            List<Matched> matched = matchByNameOrOID(prop, valueObj.toString());
            if (!matched.isEmpty())
                matches.addAll(matched);
        });
        return matches.stream().map(m -> request.apply(m).fromLeft(RequestDto::of)).toList();
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
                result.add(new Matched(prop, "Named", Collections.singletonMap("name", s)));
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
                case OID, Named -> properties.values().iterator().next();
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
