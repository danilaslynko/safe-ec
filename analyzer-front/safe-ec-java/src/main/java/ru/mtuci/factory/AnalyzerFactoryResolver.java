package ru.mtuci.factory;

import lombok.extern.slf4j.Slf4j;

import java.util.ServiceLoader;

@Slf4j
public class AnalyzerFactoryResolver
{
    private static volatile AnalyzerFactory INSTANCE;

    public static AnalyzerFactory resolveFactory()
    {
        if (INSTANCE != null)
            return INSTANCE;

        synchronized (AnalyzerFactory.class)
        {
            if (INSTANCE != null)
                return INSTANCE;

            var loader = ServiceLoader.load(AnalyzerFactory.class);
            var services = loader.stream().toList();
            if (services.size() > 1)
                throw new IllegalStateException("Only one custom analyzer factory can be registered, check SPI configuration");

            INSTANCE = services.isEmpty() ? new DefaultAnalyzerFactory() : services.get(0).get();
            log.debug("Using {} as analyzer factory", INSTANCE);
        }

        return INSTANCE;
    }
}
