package ru.mtuci.swiftconnector.config;

import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;
import ru.CryptoPro.ssl.tomcat.jsse.JCPJSSEImplementation;

@Component
public class CryptoProTomcatConfigCustom implements WebServerFactoryCustomizer<TomcatServletWebServerFactory>
{
    @Override
    public void customize(TomcatServletWebServerFactory factory)
    {
        factory.addConnectorCustomizers(connector -> {
            Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
            protocol.setSslImplementationName(JCPJSSEImplementation.class.getCanonicalName());
        });
    }
}
