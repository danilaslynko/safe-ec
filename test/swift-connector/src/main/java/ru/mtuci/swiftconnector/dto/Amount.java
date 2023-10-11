package ru.mtuci.swiftconnector.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

public record Amount(@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal value, String ccy)
{
}
