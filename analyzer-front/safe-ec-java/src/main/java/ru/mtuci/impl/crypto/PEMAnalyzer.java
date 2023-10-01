package ru.mtuci.impl.crypto;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.mutable.MutableObject;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import ru.mtuci.base.RequestingAnalyzer;
import ru.mtuci.net.Response;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;

@Slf4j
public class PEMAnalyzer extends RequestingAnalyzer
{
    public static final List<String> MIME_TYPES = Arrays.asList("application/x-pem-file");

    public PEMAnalyzer(Path path)
    {
        super(path);
    }

    @Override
    @SneakyThrows
    public List<Future<Response>> makeFutureResponses()
    {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null)
            Security.addProvider(new BouncyCastleProvider());

        PEMParser pemParser = new PEMParser(Files.newBufferedReader(path));
        PEMParserIterable iterableParser = new PEMParserIterable(pemParser);
        return ASN1Utils.makeRequests(iterableParser, this::request);
    }

    private record PEMParserIterable(PEMParser parser) implements Iterable<Object>
    {
        @Override
        public Iterator<Object> iterator()
        {
            MutableObject<Object> next = new MutableObject<>();
            return new Iterator<>()
            {
                @Override
                @SneakyThrows
                public boolean hasNext()
                {
                    next.setValue(parser.readObject());
                    return next.getValue() != null;
                }

                @Override
                public Object next()
                {
                    Object value = next.getValue();
                    next.setValue(null);
                    return value;
                }
            };
        }
    }
}
