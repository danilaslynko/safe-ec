package ru.mtuci.swiftconnector;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties("swift-connector.exchange")
public class ExchangeConfig
{
    private String messagesDir;
    private String senderBic;
    private String receiverBic;
}
