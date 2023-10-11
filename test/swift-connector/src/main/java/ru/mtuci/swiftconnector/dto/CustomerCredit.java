package ru.mtuci.swiftconnector.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record CustomerCredit(String messageId,
                             PaymentIdentification paymentIdentification, String bankOperation,
                             Amount amount, @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate settlementDate,
                             Party debtor, Account debtorAccount, Party debtorAgent,
                             Party creditor, Account creditorAccount, Party creditorAgent)
{
}
