package ru.mtuci.swiftconnector;

import lombok.SneakyThrows;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import ru.CryptoPro.Crypto.CryptoProvider;
import ru.CryptoPro.JCP.JCP;
import ru.CryptoPro.reprov.RevCheck;

import java.security.Security;

@SpringBootApplication(scanBasePackages = "ru.mtuci.swiftconnector")
public class SwiftConnectorApplication
{
    @SneakyThrows
    public static void main(String[] args)
    {
        Security.addProvider(new BouncyCastleProvider());
        Security.addProvider(new JCP());
        Security.addProvider(new RevCheck());
        Security.addProvider(new CryptoProvider());

        SpringApplication.run(SwiftConnectorApplication.class, args);
    }
}
