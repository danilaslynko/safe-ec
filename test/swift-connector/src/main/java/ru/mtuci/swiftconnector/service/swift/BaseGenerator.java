package ru.mtuci.swiftconnector.service.swift;

import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import ru.mtuci.swiftconnector.ExchangeConfig;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public abstract class BaseGenerator<T>
{
    private static final String DATA_PDU_TEMPLATE = """
            <Saa:DataPDU xmlns:Saa="urn:swift:saa:xsd:saa.2.0">
                <Saa:Revision>2.0.15</Saa:Revision>
                <Saa:Header>
                    <Saa:Message>
                        <Saa:MessageIdentifier>%s</Saa:MessageIdentifier>
                        <Saa:Format>AnyXML</Saa:Format>
                        <Saa:Sender>
                            <Saa:BIC12>%s</Saa:BIC12>
                        </Saa:Sender>
                        <Saa:Receiver>
                            <Saa:BIC12>%s</Saa:BIC12>
                        </Saa:Receiver>
                    </Saa:Message>
                </Saa:Header>
                <Saa:Body>
            %s
            %s
                </Saa:Body>
            </Saa:DataPDU>
            """.stripIndent();

    private static final DatatypeFactory datatypeFactory;
    static
    {
        try
        {
            datatypeFactory = DatatypeFactory.newInstance();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Autowired
    private AppHeaderGenerator appHeaderOps;
    @Autowired
    private ExchangeConfig exchangeConfig;

    @SneakyThrows
    protected static String marshal(Object obj)
    {
        var sw = new StringWriter();
        JAXBContext context = JAXBContext.newInstance(obj.getClass());
        Marshaller m = context.createMarshaller();
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        m.setProperty(Marshaller.JAXB_FRAGMENT, true);
        m.marshal(obj, sw);
        return sw.toString();
    }

    public String generate(T obj)
    {
        var now = new Date();
        var appHeader = appHeaderOps.generate(getMessageType(), now);
        var document = generateDocument(obj, now);
        return DATA_PDU_TEMPLATE.formatted(getMessageType(), exchangeConfig.getSenderBic(), exchangeConfig.getReceiverBic(), appHeader, document);
    }

    protected abstract String generateDocument(T obj, Date now);
    protected abstract String getMessageType();

    public static XMLGregorianCalendar asCalendar(LocalDate date)
    {
        var sdCalendar = new GregorianCalendar(TimeZone.getTimeZone(ZoneOffset.UTC));
        sdCalendar.set(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        return datatypeFactory.newXMLGregorianCalendar(sdCalendar);
    }

    public static XMLGregorianCalendar asCalendar(Date now)
    {
        var cdCalendar = new GregorianCalendar(TimeZone.getTimeZone(ZoneOffset.UTC));
        cdCalendar.setTime(now);
        return datatypeFactory.newXMLGregorianCalendar(cdCalendar);
    }
}
