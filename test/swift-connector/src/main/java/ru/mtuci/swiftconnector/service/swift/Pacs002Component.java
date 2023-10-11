package ru.mtuci.swiftconnector.service.swift;

import iso.std.iso._20022.tech.xsd.pacs_002_001.Document;
import iso.std.iso._20022.tech.xsd.pacs_002_001.FIToFIPaymentStatusReportV10;
import iso.std.iso._20022.tech.xsd.pacs_002_001.GroupHeader911;
import iso.std.iso._20022.tech.xsd.pacs_002_001.PaymentTransaction1101;
import iso.std.iso._20022.tech.xsd.pacs_002_001.StatusReason6Choice1;
import iso.std.iso._20022.tech.xsd.pacs_002_001.StatusReasonInformation121;
import jakarta.xml.bind.JAXB;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import ru.mtuci.swiftconnector.dto.PaymentIdentification;
import ru.mtuci.swiftconnector.dto.StatusMessage;
import ru.mtuci.swiftconnector.utils.Utils;

import javax.xml.transform.dom.DOMSource;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class Pacs002Component extends BaseGenerator<StatusMessage>
{
    public StatusMessage parse(String xml)
    {
        var domDoc = Utils.withDocumentBuilder(b -> b.parse(new InputSource(new StringReader(xml))));
        var nodes = domDoc.getElementsByTagNameNS("urn:iso:std:iso:20022:tech:xsd:pacs.002.001.10", "Document");
        if (nodes.getLength() == 0)
            throw new RuntimeException("Document node not found");

        var documentNode = nodes.item(0);
        var document = JAXB.unmarshal(new DOMSource(documentNode), Document.class);
        var statusReport = document.getFIToFIPmtStsRpt();
        var txInfAndSts = statusReport.getTxInfAndSts();
        var statusInfo = "";
        if (txInfAndSts.getStsRsnInf() != null)
        {
            if (txInfAndSts.getStsRsnInf().getRsn() != null)
                statusInfo += StringUtils.firstNonEmpty(txInfAndSts.getStsRsnInf().getRsn().getCd(), txInfAndSts.getStsRsnInf().getRsn().getPrtry());

            if (!txInfAndSts.getStsRsnInf().getAddtlInves().isEmpty())
            {
                if (!statusInfo.isEmpty())
                    statusInfo += " - ";

                statusInfo += String.join("\n" + txInfAndSts.getStsRsnInf().getAddtlInves());
            }
        }
        return new StatusMessage(new PaymentIdentification(txInfAndSts.getOrgnlEndToEndId(), txInfAndSts.getOrgnlTxId(), txInfAndSts.getOrgnlUETR()), txInfAndSts.getTxSts(), statusInfo);
    }

    @Override
    protected String generateDocument(StatusMessage obj, Date now)
    {
        var document = new Document();
        var statusReport = new FIToFIPaymentStatusReportV10();
        var grpHdr = new GroupHeader911();
        grpHdr.setMsgId("MSG-" + System.nanoTime());
        grpHdr.setCreDtTm(asCalendar(now));
        statusReport.setGrpHdr(grpHdr);

        var txInfAndSts = new PaymentTransaction1101();
        txInfAndSts.setTxSts(obj.status());
        txInfAndSts.setOrgnlEndToEndId(obj.originalIdentification().endToEndId());
        txInfAndSts.setOrgnlTxId(obj.originalIdentification().transactionId());
        if (StringUtils.isNotEmpty(obj.info()))
        {
            var split = obj.info().split("\\s+-\\s+", 2);
            var reason = new StatusReasonInformation121();
            var reasonChoice = new StatusReason6Choice1();
            reasonChoice.setPrtry(split[0]);
            reason.setRsn(reasonChoice);
            if (split.length > 1)
            {
                var info = split[1];
                int chunks = (info.length() + 104) / 105;
                List<String> splitInfo = new ArrayList<>(chunks);
                for (int i = 0; i < chunks; ++i)
                {
                    String chunk = info.substring(i * 105, Math.min(info.length(), (i + 1) * 105));
                    splitInfo.add(chunk);
                }
                reason.getAddtlInves().addAll(splitInfo);
            }

            txInfAndSts.setStsRsnInf(reason);
        }
        statusReport.setTxInfAndSts(txInfAndSts);
        document.setFIToFIPmtStsRpt(statusReport);
        return marshal(document);
    }

    @Override
    protected String getMessageType()
    {
        return "pacs.002.001.10";
    }
}
