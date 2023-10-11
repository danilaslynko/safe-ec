package ru.mtuci.swiftconnector.service.swift;

import iso.std.iso._20022.tech.xsd.pacs_008_001.AccountIdentification4Choice1;
import iso.std.iso._20022.tech.xsd.pacs_008_001.BranchAndFinancialInstitutionIdentification61;
import iso.std.iso._20022.tech.xsd.pacs_008_001.BranchAndFinancialInstitutionIdentification63;
import iso.std.iso._20022.tech.xsd.pacs_008_001.CBPRAmount1;
import iso.std.iso._20022.tech.xsd.pacs_008_001.CashAccount381;
import iso.std.iso._20022.tech.xsd.pacs_008_001.CreditTransferTransaction391;
import iso.std.iso._20022.tech.xsd.pacs_008_001.Document;
import iso.std.iso._20022.tech.xsd.pacs_008_001.FIToFICustomerCreditTransferV08;
import iso.std.iso._20022.tech.xsd.pacs_008_001.FinancialInstitutionIdentification181;
import iso.std.iso._20022.tech.xsd.pacs_008_001.GenericAccountIdentification11;
import iso.std.iso._20022.tech.xsd.pacs_008_001.GroupHeader931;
import iso.std.iso._20022.tech.xsd.pacs_008_001.LocalInstrument2Choice1;
import iso.std.iso._20022.tech.xsd.pacs_008_001.OrganisationIdentification291;
import iso.std.iso._20022.tech.xsd.pacs_008_001.OrganisationIdentification292;
import iso.std.iso._20022.tech.xsd.pacs_008_001.Party38Choice1;
import iso.std.iso._20022.tech.xsd.pacs_008_001.Party38Choice2;
import iso.std.iso._20022.tech.xsd.pacs_008_001.PartyIdentification1352;
import iso.std.iso._20022.tech.xsd.pacs_008_001.PartyIdentification1353;
import iso.std.iso._20022.tech.xsd.pacs_008_001.PaymentIdentification71;
import iso.std.iso._20022.tech.xsd.pacs_008_001.PaymentTypeInformation281;
import iso.std.iso._20022.tech.xsd.pacs_008_001.PostalAddress241;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import ru.mtuci.swiftconnector.dto.Account;
import ru.mtuci.swiftconnector.dto.CustomerCredit;
import ru.mtuci.swiftconnector.dto.Party;

import java.util.Date;
import java.util.List;

@Component
public class Pacs008Generator extends BaseGenerator<CustomerCredit>
{
    @Override
    @SneakyThrows
    protected String generateDocument(CustomerCredit obj, Date now)
    {
        var document = new Document();
        var transfer = new FIToFICustomerCreditTransferV08();
        document.setFIToFICstmrCdtTrf(transfer);

        var grpHdr = new GroupHeader931();

        grpHdr.setCreDtTm(asCalendar(now));
        grpHdr.setMsgId(obj.messageId());
        grpHdr.setNbOfTxs("1");
        transfer.setGrpHdr(grpHdr);

        var transaction = new CreditTransferTransaction391();

        var pmtId = new PaymentIdentification71();
        pmtId.setEndToEndId(obj.paymentIdentification().endToEndId());
        pmtId.setTxId(obj.paymentIdentification().transactionId());
        pmtId.setUETR(obj.paymentIdentification().uetr());
        transaction.setPmtId(pmtId);

        transaction.setDbtr(makeCustomer1(obj.debtor()));
        transaction.setDbtrAcct(makeAccount(obj.debtorAccount()));
        transaction.setDbtrAgt(makeAgent1(obj.debtorAgent()));

        transaction.setCdtr(makeCustomer2(obj.creditor()));
        transaction.setCdtrAcct(makeAccount(obj.creditorAccount()));
        transaction.setCdtrAgt(makeAgent2(obj.creditorAgent()));

        var amount = new CBPRAmount1();
        amount.setCcy(obj.amount().ccy());
        amount.setValue(obj.amount().value());
        transaction.setIntrBkSttlmAmt(amount);

        transaction.setIntrBkSttlmDt(asCalendar(obj.settlementDate()));

        var pmtTpInf = new PaymentTypeInformation281();
        var localInstrument = new LocalInstrument2Choice1();
        localInstrument.setPrtry(obj.bankOperation());
        pmtTpInf.setLclInstrm(localInstrument);
        transaction.setPmtTpInf(pmtTpInf);

        transfer.setCdtTrfTxInf(transaction);

        return marshal(document);
    }

    private PartyIdentification1352 makeCustomer1(Party party)
    {
        if (party == null)
            return null;

        var jaxb = new PartyIdentification1352();
        jaxb.setNm(party.name());
        if (party.bic() != null)
        {
            var id = new Party38Choice2();
            var orgId = new OrganisationIdentification292();
            orgId.setAnyBIC(party.bic());
            id.setOrgId(orgId);
            jaxb.setId(id);
        }
        if (party.address() != null)
        {
            var address = new PostalAddress241();
            address.getAdrLines().addAll(List.of(party.address().split("\r\n|\n")));
            jaxb.setPstlAdr(address);
        }
        return jaxb;
    }

    private PartyIdentification1353 makeCustomer2(Party party)
    {
        if (party == null)
            return null;

        var jaxb = new PartyIdentification1353();
        jaxb.setNm(party.name());
        if (party.bic() != null)
        {
            var id = new Party38Choice1();
            var orgId = new OrganisationIdentification291();
            orgId.setAnyBIC(party.bic());
            id.setOrgId(orgId);
            jaxb.setId(id);
        }
        if (party.address() != null)
        {
            var address = new PostalAddress241();
            address.getAdrLines().addAll(List.of(party.address().split("\r\n|\n")));
            jaxb.setPstlAdr(address);
        }
        return jaxb;
    }

    private BranchAndFinancialInstitutionIdentification61 makeAgent1(Party party)
    {
        if (party == null)
            return null;

        var jaxb = new BranchAndFinancialInstitutionIdentification61();
        var fiid = new FinancialInstitutionIdentification181();
        fiid.setNm(party.name());
        fiid.setLEI(party.bic());
        if (party.address() != null)
        {
            var address = new PostalAddress241();
            address.getAdrLines().addAll(List.of(party.address().split("\r\n|\n")));
            fiid.setPstlAdr(address);
        }
        jaxb.setFinInstnId(fiid);
        return jaxb;
    }

    private BranchAndFinancialInstitutionIdentification63 makeAgent2(Party party)
    {
        if (party == null)
            return null;

        var jaxb = new BranchAndFinancialInstitutionIdentification63();
        var fiid = new FinancialInstitutionIdentification181();
        fiid.setNm(party.name());
        fiid.setLEI(party.bic());
        if (party.address() != null)
        {
            var address = new PostalAddress241();
            address.getAdrLines().addAll(List.of(party.address().split("\r\n|\n")));
            fiid.setPstlAdr(address);
        }
        jaxb.setFinInstnId(fiid);
        return jaxb;
    }

    private CashAccount381 makeAccount(Account account)
    {
        if (account == null)
            return null;

        var jaxb = new CashAccount381();
        jaxb.setCcy(account.currency());
        jaxb.setNm(account.name());
        var id = new AccountIdentification4Choice1();
        if (account.iban())
        {
            id.setIBAN(account.id());
        }
        else
        {
            var genericId = new GenericAccountIdentification11();
            genericId.setId(account.id());
            id.setOthr(genericId);
        }
        jaxb.setId(id);
        return jaxb;
    }

    @Override
    protected String getMessageType()
    {
        return "pacs.008.001.08";
    }
}
