package ru.mtuci;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.SneakyThrows;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class Config
{
    public static final Config INSTANCE;

    static
    {
        Config instance = null;
        try
        {
            var configPath = Paths.get("safe-ec.yaml").toAbsolutePath();

            if (!Files.exists(configPath))
            {
                configPath = Paths.get("safe-ec.yml").toAbsolutePath();
            }

            if (!Files.exists(configPath))
            {
                var jarPath = Paths.get(Config.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                configPath = jarPath.getParent().resolve("safe-ec.yaml");
                if (!Files.exists(configPath))
                {
                    configPath = jarPath.resolve("save-ec.yml");
                }
            }

            InputStream _is = null;
            if (Files.exists(configPath))
                _is = Files.newInputStream(configPath);
            if (_is == null)
                _is = Config.class.getClassLoader().getResourceAsStream("safe-ec.yaml");
            if (_is == null)
                _is = Config.class.getClassLoader().getResourceAsStream("safe-ec.yml");

            try (var is = _is)
            {
                instance = new Config(is);
            }
        }
        catch (Exception e)
        {
            ExceptionUtils.rethrow(e);
        }
        finally
        {
            INSTANCE = instance;
        }
    }

    ;

    private final Map<String, Map<String, Object>> analyzersConfig;

    @SneakyThrows
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Config(InputStream is)
    {
        Map config = new YAMLMapper().readValue(is, Map.class);
        analyzersConfig = (Map) config.get("analyzers");
    }

    public Map<String, Object> getAnalyzerConfig(String analyzer)
    {
        return analyzersConfig.get(analyzer);
    }
}
