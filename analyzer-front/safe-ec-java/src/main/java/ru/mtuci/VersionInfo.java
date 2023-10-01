package ru.mtuci;

import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.util.Properties;

@Slf4j
public record VersionInfo(String version, String author, OffsetDateTime buildDateTime)
{
    public static VersionInfo getInstance()
    {
        try (var is = VersionInfo.class.getResourceAsStream("git.properties"))
        {
            var props = new Properties();
            props.load(is);
            var version = props.getProperty("git.build.version");
            var builderName = props.getProperty("git.build.user.name");
            var builderEmail = props.getProperty("git.build.user.email");
            if (builderName != null && builderEmail != null)
                builderName += " <" + builderEmail + ">";

            var buildDateTime = OffsetDateTime.parse(props.getProperty("git.build.time"));
            return new VersionInfo(version, builderName, buildDateTime);
        }
        catch (Exception e)
        {
            log.error("Unable to get version info", e);
            return new VersionInfo(null, null, null);
        }
    }
}
