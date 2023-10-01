package ru.mtuci.net;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonDeserialize
public record Response(String reqId, Type type, String info)
{
    public enum Type
    {SUCCESS, ERROR, VULNERABLE}
}
