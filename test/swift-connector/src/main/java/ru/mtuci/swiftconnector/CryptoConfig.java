package ru.mtuci.swiftconnector;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Getter
@Setter
@Accessors(chain = true)
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties("swift-connector.crypto")
public class CryptoConfig
{
    private Path certsDir;
    private Path keyStore;
    private String keyStorePassword;
    private String keyAlias;
}
