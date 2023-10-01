package ru.mtuci.utils;

import lombok.experimental.UtilityClass;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@UtilityClass
public class VirtualPaths
{
    private final Map<Path, Path> virtualToReal = new HashMap<>();

    public void mapVirtualToReal(Path virtual, Path real)
    {
        virtualToReal.put(virtual.toAbsolutePath(), real.toAbsolutePath());
    }

    public Path resolve(Path virtual)
    {
        if (virtual == null)
            return null;

        return virtualToReal.getOrDefault(virtual.toAbsolutePath(), virtual.toAbsolutePath());
    }
}
