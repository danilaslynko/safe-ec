package ru.mtuci.impl.props;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TODO
 *  Должен извлекать параметры эллиптических кривых. Сейчас алгоритм не очень. Имеет ли он вообще смысл? Действительно
 *  ли кто-то задает параметры кривых в пропертях? Сейчас алгоритм работает так себе и реально не используется.
 *  Возможно, имеет смысл заменить его на нейросеть, но пока не смогли найти подходящий датасет.
 */
public class ECParamsExtractor {
    private final Properties properties;
    
    public ECParamsExtractor(Properties properties) {
        this.properties = (Properties) properties.clone();
    }
    
    private Matched matchGroup(List<String> keys) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("form", "weierstrass"); // TODO support for Montgomery and Edwards curves
        Boolean hex = null;
        for (String key : keys) {
            List<String> tokens = tokenize(key);
            boolean matched = true;
            String numeric = null;
            if (containsAnyIgnoreCase(tokens, "cofactor", "h") && !parameters.containsKey("cofactor"))
                parameters.put("cofactor", (numeric = properties.getProperty(key)));
            else if (containsAnyIgnoreCase(tokens, "x") && !parameters.containsKey("base.x"))
                parameters.put("base.x", (numeric = properties.getProperty(key)));
            else if (containsAnyIgnoreCase(tokens, "y") && !parameters.containsKey("base.y"))
                parameters.put("base.y", (numeric = properties.getProperty(key)));
            else if (containsAnyIgnoreCase(tokens, "p", "prime") && !parameters.containsKey("p"))
                parameters.put("p", (numeric = properties.getProperty(key)));
            else if (containsAnyIgnoreCase(tokens, "order", "l", "n") && !parameters.containsKey("order"))
                parameters.put("order", (numeric = properties.getProperty(key)));
            else if (containsAnyIgnoreCase(tokens, "a") && !parameters.containsKey("weierstrass.a"))
                parameters.put("weierstrass.a", (numeric = properties.getProperty(key)));
            else if (containsAnyIgnoreCase(tokens, "b") && !parameters.containsKey("weierstrass.b"))
                parameters.put("weierstrass.b", (numeric = properties.getProperty(key)));
            else
                matched = false;

            if (matched) {
                properties.remove(key);
                hex = isHex(numeric);
                if (hex == null)
                    return null;
            }
        }
        if (hex == null || parameters.size() < 6)
            return null;

        parameters.put("hex", hex ? "true" : "false");

        return new Matched("Params", parameters);
    }
    
    // cool_fast|test,for.aTestIBMParsesWellS -> [cool, fast, test, for, a, test, ibm, parses, well, s]
    public static List<String> tokenize(String str) {
        List<String> list = new ArrayList<>();
        for (String s : str.split("[\\W_]+")) {
            List<String> subTokens = new ArrayList<>();
            if (s.length() == 1) {
                subTokens.add(s);
            }
            else
            {
                StringBuilder currentBuilder = new StringBuilder();
                for (int i = 0; i < s.length(); i++) {
                    Character curr = s.charAt(i);
                    Character next = i == s.length() - 1 ? null : s.charAt(i+1);
                    currentBuilder.append(curr);
                    if (next == null || Character.isUpperCase(next)) {
                        subTokens.add(currentBuilder.toString());
                        currentBuilder = new StringBuilder();
                    }
                }
                StringBuilder abbrev = new StringBuilder();
                List<String> realSubTokens = new ArrayList<>();
                for (String subToken : subTokens) {
                    if (subToken.length() == 1 && Character.isUpperCase(subToken.charAt(0))) {
                        System.out.printf("appended %s%n", subToken);
                        abbrev.append(subToken);
                    }
                    else {
                        if (abbrev.length() > 0) {
                            realSubTokens.add(abbrev.toString().toLowerCase());
                            abbrev = new StringBuilder();
                        }
                        realSubTokens.add(subToken.toLowerCase());
                    }
                    System.out.println(subToken);
                    System.out.println(abbrev);
                }
                if (abbrev.length() > 0)
                    realSubTokens.add(abbrev.toString().toLowerCase());

                subTokens = realSubTokens;
            }
            list.addAll(subTokens);
        }
        return list.stream().map(String::toLowerCase).collect(Collectors.toList());
    }

    private static boolean containsAnyIgnoreCase(Collection<String> col, String... elements) {
        for (String e : elements) {
            for (String s : col) {
                if (s.equalsIgnoreCase(e))
                    return true;
            }
        }
        return false;
    }

    private static Boolean isHex(String str) {
        if (!NumberUtils.isCreatable(str))
            return null;

        return StringUtils.containsAnyIgnoreCase("a", "b", "c", "d", "e", "f");
    }
}
