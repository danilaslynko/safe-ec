package ru.mtuci.swiftconnector.config;

import lombok.SneakyThrows;
import org.apache.coyote.http11.AbstractHttp11JsseProtocol;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundleKey;
import org.springframework.boot.ssl.SslManagerBundle;
import org.springframework.boot.ssl.SslOptions;
import org.springframework.boot.ssl.SslStoreBundle;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.boot.web.server.WebServerSslBundle;

import java.security.KeyStore;

@Component
public class CryptoProTomcatConfigCustom implements WebServerFactoryCustomizer<TomcatServletWebServerFactory>
{
//    @Bean
//    public TomcatServletWebServerFactory createFactory()
//    {
//        var factory = new TomcatServletWebServerFactory();
//        factory.addConnectorCustomizers(connector -> {
//        });
//        return factory;
//    }

    @Override
    public void customize(TomcatServletWebServerFactory factory)
    {
        factory.addConnectorCustomizers(connector -> {
//            connector.setScheme("https");
//            connector.setPort(8443);
//            connector.setSecure(true);
//            connector.setEnableLookups(false);
            Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
            protocol.setSslImplementationName("ru.CryptoPro.ssl.tomcat.jsse.JCPJSSEImplementation");
//            protocol.setAlgorithm("GostX509");
//            protocol.setTruststoreAlgorithm("GostX509");
//            protocol.setKeystoreType("HDImageStore");
//            protocol.setKeystoreFile("/root/certStore");
//            protocol.setKeystorePass("123");
//            protocol.setKeystoreProvider("JCP");
//            protocol.setSSLCipherSuite("TLS_CIPHER_2012");
//            protocol.setSSLProtocol("GostTLSv1.2");
//            protocol.setSslEnabledProtocols("TLSv1.2");
//            protocol.setDisableUploadTimeout(true);
//            protocol.setSSLEnabled(true);
        });
    }
}
