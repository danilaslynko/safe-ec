package ru.mtuci.impl.props;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import ru.mtuci.base.RequestingAnalyzer;
import ru.mtuci.utils.Either;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class YAMLAnalyzer extends RequestingAnalyzer
{
    public static final List<String> EXTENSIONS = Arrays.asList(".yaml", ".yml");
    public static final List<String> MIME_TYPES = Arrays.asList("application/x-yaml", "text/yaml");

    private static final YAMLMapper mapper = new YAMLMapper();

    public YAMLAnalyzer(Path path)
    {
        super(path);
    }

    @SneakyThrows
    @Override
    protected List<RequestDto> makeRequests()
    {
        @SuppressWarnings("unchecked")
        Map<String, Object> mapLoaded = mapper.readValue(Files.newBufferedReader(path), Map.class);
        var map = flattenYaml(mapLoaded);
        var properties = new Properties();
        properties.putAll(map);
        return PropertiesAnalyzer.analyzeProps(properties, m -> Either.right(request(m.toRequest())));
    }

    private static Map<String, Object> flattenYaml(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        buildFlattenedMap(result, source, null);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void buildFlattenedMap(Map<String, Object> result, Map<String, Object> source, String path) {
        source.forEach((key, value) -> {
            if (!StringUtils.isBlank(path))
                key = path + (key.startsWith("[") ? key : '.' + key);
            if (value instanceof String) {
                result.put(key, value);
            } else if (value instanceof Map) {
                buildFlattenedMap(result, (Map<String, Object>) value, key);
            } else if (value instanceof Collection<?> c) {
                int count = 0;
                for (Object object : c)
                    buildFlattenedMap(result, Collections.singletonMap("[" + (count++) + "]", object), key);
            } else {
                result.put(key, value != null ? "" + value : "");
            }
        });
    }
}
