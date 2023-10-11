package ru.mtuci.swiftconnector.service.swift;

import iso.std.iso._20022.tech.xsd.head_001_001.AppHdr;
import iso.std.iso._20022.tech.xsd.head_001_001.BranchAndFinancialInstitutionIdentification6;
import iso.std.iso._20022.tech.xsd.head_001_001.FinancialInstitutionIdentification18;
import iso.std.iso._20022.tech.xsd.head_001_001.Party44Choice;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import ru.mtuci.swiftconnector.ExchangeConfig;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import java.io.StringWriter;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class AppHeaderGenerator
{
    private final ExchangeConfig exchangeConfig;

    @SneakyThrows
    public String generate(String messageType, Date now)
    {
        var appHeader = new AppHdr();
        appHeader.setBizMsgIdr("BIZ-" + System.nanoTime());
        appHeader.setCreDt(BaseGenerator.asCalendar(now));

        var from = new Party44Choice();
        var fromAgent = new BranchAndFinancialInstitutionIdentification6();
        var fromId = new FinancialInstitutionIdentification18();
        fromId.setBICFI(exchangeConfig.getSenderBic());
        fromAgent.setFinInstnId(fromId);
        from.setFIId(fromAgent);
        appHeader.setFr(from);

        var to = new Party44Choice();
        var toAgent = new BranchAndFinancialInstitutionIdentification6();
        var toId = new FinancialInstitutionIdentification18();
        toId.setBICFI(exchangeConfig.getReceiverBic());
        toAgent.setFinInstnId(toId);
        to.setFIId(toAgent);
        appHeader.setFr(to);

        appHeader.setPrty("0010");
        appHeader.setMsgDefIdr(messageType);
        appHeader.setBizSvc("amdbrtgs");

        return BaseGenerator.marshal(appHeader);
    }
}
