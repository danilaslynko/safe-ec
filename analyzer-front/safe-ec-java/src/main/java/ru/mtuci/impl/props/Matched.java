package ru.mtuci.impl.props;

import ru.mtuci.net.Request;

import java.math.BigInteger;
import java.util.Map;

record Matched(String type, Map<String, String> properties) {
    Request toRequest() {
        Request.Type type = Request.Type.valueOf(this.type);
        Object data = switch (type) {
            case OID, Named -> this.properties.values().iterator().next();
            // TODO Действительно ли кто-то будет в пропертях передавать параметры кривой?
            case Params -> {
                String form = properties.getOrDefault("form", "weierstrass");
                yield switch (form) {
                    case "weierstrass" -> fromWeierstrass(properties);
                    default -> throw new IllegalStateException("Unexpected value: " + form);
                };
            }//Gavno
        };
        return Request.of(type, data);
    }
    
    private static Request.Params fromWeierstrass(Map<String, String> properties) {
        int radix = "true".equalsIgnoreCase(properties.get("hex")) ? 16 : 10;
        BigInteger p = bi(properties.get("p"), radix);
        BigInteger a = bi(properties.get("weierstrass.a"), radix);
        BigInteger b = bi(properties.get("weierstrass.b"), radix);
        BigInteger x = bi(properties.get("base.x"), radix);
        BigInteger y = bi(properties.get("base.y"), radix);
        BigInteger n = bi(properties.get("n"), radix);
        String h = properties.get("h");
        String seed = properties.get("seed");
        return new Request.Params("prime", p, a, b, x, y, n, h, seed);
    }
    
    private static BigInteger bi(String s, int radix) {
        return new BigInteger(s, radix);
    }
}
