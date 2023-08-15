package ru.mtuci.net;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.SneakyThrows;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record Request(String id, Type type, String value) {
    public enum Type { OID, Named, Params }
    
    private static final AtomicLong counter = new AtomicLong(1);    
    private static final ObjectMapper mapper = new ObjectMapper();
    
    @SneakyThrows
    public static Request of(Type type, Object data) {
        String value;
        if (data instanceof String s)
            value = s;
        else 
            value = mapper.writeValueAsString(data);
        
        return new Request(String.valueOf(counter.getAndIncrement()), type, value);
    }
    
    public record Params(String type,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigInteger fp,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigInteger a,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigInteger b,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigInteger x,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigInteger y,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigInteger n,
                         String h,
                         String seed) {}
}
