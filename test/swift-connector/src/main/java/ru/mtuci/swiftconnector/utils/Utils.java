package ru.mtuci.swiftconnector.utils;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.util.function.ThrowingFunction;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

@UtilityClass
public class Utils
{
    private static final ObjectPool<DocumentBuilder> docBuilderPool = new ObjectPool<>(() -> {
        try
        {
            var dbf = DocumentBuilderFactory.newInstance();
            String feature;
            // This is the PRIMARY defense. If DTDs (doctypes) are disallowed, almost all XML entity attacks are prevented
            feature = "http://apache.org/xml/features/disallow-doctype-decl";
            dbf.setFeature(feature, true);

            // If you can't completely disable DTDs, then at least do the following:
            feature = "http://xml.org/sax/features/external-general-entities";
            dbf.setFeature(feature, false);

            feature = "http://xml.org/sax/features/external-parameter-entities";
            dbf.setFeature(feature, false);

            // Disable external DTDs as well
            feature = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
            dbf.setFeature(feature, false);

            // and these as well, per Timothy Morgan's 2014 paper: "XML Schema, DTD, and Entity Attacks"
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);

            dbf.setNamespaceAware(true);
            return dbf.newDocumentBuilder();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    });

    public static <T> T withDocumentBuilder(ThrowingFunction<DocumentBuilder, T> action)
    {
        try (var builder = docBuilderPool.get())
        {
            return action.apply(builder.getObject());
        }
        catch (Exception e)
        {
            return ExceptionUtils.rethrow(e);
        }
    }
}
