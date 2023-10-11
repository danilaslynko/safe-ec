package ru.mtuci.swiftconnector.dto;

public record StatusMessage(PaymentIdentification originalIdentification, String status, String info)
{
}
