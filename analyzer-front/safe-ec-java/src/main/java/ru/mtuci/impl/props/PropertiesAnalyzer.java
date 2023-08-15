package ru.mtuci.impl.props;

import io.churchkey.asn1.Oid;
import io.churchkey.ec.Curve;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import ru.mtuci.impl.Analyzer;
import ru.mtuci.net.Response;
import ru.mtuci.net.SafeEcClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

@Slf4j
public class PropertiesAnalyzer extends Analyzer {    
    public static final String EXTENSION = ".properties";
    public static final List<String> MIME_TYPES = List.of("text/x-java-properties");
    
    private static final Pattern OID_PATTERN = Pattern.compile("^([0-2])((\\.0)|(\\.[1-9][0-9]*))*$");
    
    public PropertiesAnalyzer(Path path) {
        super(path);
    }

    @Override
    @SneakyThrows
    public void analyze() {
        try {
            Properties props = new Properties();
            props.load(Files.newInputStream(path));
            checkProps(props);
        }
        catch (Exception e) {
            log.error("Unable to check propertyfile {}", path, e);
        }
    }
    
    private void checkProps(Properties props) {
        Properties toIterate = (Properties) props.clone();
        List<Matched> matches = new ArrayList<>();
        List<String> keysForFuzzySearch = new ArrayList<>();
        toIterate.forEach((keyObj, valueObj) -> {
            if (StringUtils.containsAny(keyObj.toString(), "exclud", "exclusion"))
                return;
            
            List<Matched> matched = matchByNameOrOID(valueObj.toString());
            if (!matched.isEmpty())
                matches.addAll(matched);
            else 
                keysForFuzzySearch.add(keyObj.toString());
        });

        // TODO perform fuzzy search?
        
        List<Future<Response>> futures = new ArrayList<>();
        for (Matched match : matches) {
            SafeEcClient client = SafeEcClient.getInstance();
            futures.add(client.send(match.toRequest()));
        }

        try {
            for (Future<Response> future : futures) {
                Response response = future.get();
                switch (response.type()) {
                    case ERROR -> addError("EC check request failed: reqId={}, info='{}'", response.reqId(), response.info());
                    case VULNERABLE -> addError(response.info());
                }
            }
        }
        catch (Exception e) {
            log.error("Properties analysis failed", e);
            addError("Properties analysis failed", e);
        }
    }
    
    
    private List<Matched> matchByNameOrOID(String str) {
        List<Matched> result = new ArrayList<>();
        if (StringUtils.isEmpty(str))
            return result;

        for (String s : str.split(",;|")) {
            if (OID_PATTERN.matcher(s).matches() && Curve.resolve(Oid.fromString(s)) != null)
                result.add(new Matched("OID", Collections.singletonMap("oid", s)));

            try {
                Curve.resolve(s);
                result.add(new Matched("Named", Collections.singletonMap("name", s)));
            } catch (IllegalArgumentException e) {
                // no curve found
            }
        }
        
        return result;
    }
}
