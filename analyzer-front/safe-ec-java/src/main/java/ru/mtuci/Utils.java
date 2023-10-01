package ru.mtuci;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.churchkey.ec.Curve;
import lombok.SneakyThrows;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.cert.CertificateFactory;
import java.util.Collection;

public class Utils
{
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final ThreadLocal<CertificateFactory> x509CertFactories = ThreadLocal.withInitial(() -> {
        try
        {
            return CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME);
        }
        catch (Exception e)
        {
            return ExceptionUtils.rethrow(e);
        }
    });

    public static CertificateFactory getX509CertificateFactory()
    {
        return x509CertFactories.get();
    }

    public static int size(Collection<?> col)
    {
        return col == null ? 0 : col.size();
    }

    public static Curve curveByName(String name)
    {
        try
        {
            return Curve.resolve(name);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public static String toJson(Object obj)
    {
        return toJson(mapper.writer(), obj);
    }

    public static String toJsonIndented(Object obj)
    {
        var writer = mapper.writer();
        writer.with(SerializationFeature.INDENT_OUTPUT);
        return toJson(writer, obj);
    }

    @SneakyThrows
    private static String toJson(ObjectWriter writer, Object obj)
    {
        return writer.writeValueAsString(obj);
    }

    @SneakyThrows
    public static <T> T fromJson(byte[] json, Class<T> type, Class<?>... params)
    {
        var javaType = params.length == 0 ? mapper.getTypeFactory().constructType(type) : mapper.getTypeFactory().constructParametricType(type, params);
        return mapper.readValue(json, javaType);
    }
}
