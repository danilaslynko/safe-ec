package ru.mtuci.test;

import lombok.SneakyThrows;

import java.nio.file.Path;
import java.nio.file.Paths;

public class TestUtils
{
    @SneakyThrows
    public static Path getResourcePath(String resource)
    {
        if (!resource.startsWith("/"))
            resource = "/" + resource;

        return Paths.get(TestUtils.class.getResource(resource).toURI());
    }
}
