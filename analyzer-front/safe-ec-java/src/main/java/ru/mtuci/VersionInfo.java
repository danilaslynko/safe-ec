package ru.mtuci;

import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Properties;

@Slf4j
public record VersionInfo(String version, String author, OffsetDateTime buildDateTime)
{
    public static DateTimeFormatter formatter = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .optionalStart().appendOffset("+HH:MM", "+00:00").optionalEnd()
            .optionalStart().appendOffset("+HHMM", "+0000").optionalEnd()
            .optionalStart().appendOffset("+HH", "Z").optionalEnd()
            .toFormatter();

    public static VersionInfo getInstance()
    {
        try (var is = VersionInfo.class.getResourceAsStream("/git.properties"))
        {
            var props = new Properties();
            props.load(is);
            var version = props.getProperty("git.build.version");
            var builderName = props.getProperty("git.build.user.name");
            var builderEmail = props.getProperty("git.build.user.email");
            if (builderName != null && builderEmail != null)
                builderName += " <" + builderEmail + ">";

            var buildDateTime = OffsetDateTime.parse(props.getProperty("git.build.time"), formatter);
            return new VersionInfo(version, builderName, buildDateTime);
        }
        catch (Exception e)
        {
            log.error("Unable to get version info", e);
            return new VersionInfo(null, null, null);
        }
    }
}
