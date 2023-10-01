package ru.mtuci.net;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.churchkey.shade.util.Hex;
import lombok.SneakyThrows;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.math.ec.ECAlgorithms;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import ru.mtuci.Utils;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record Request(String id, Type type, String value) {
    public enum Type { OID, Named, Params }
    
    private static final AtomicLong counter = new AtomicLong(1);

    public static void resetCounter()
    {
        counter.set(1);
    }

    @SneakyThrows
    public static Request of(Type type, Object data) {
        String value;
        if (data instanceof String s)
            value = s;
        else 
            value = Utils.toJson(data);
        
        return new Request(String.valueOf(counter.getAndIncrement()), type, value);
    }
    
    public record Params(String type,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigInteger fp,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigInteger a,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigInteger b,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigInteger x,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigInteger y,
                         @JsonFormat(shape = JsonFormat.Shape.STRING) BigInteger n,
                         BigInteger h,
                         String seed)
    {
        @SneakyThrows
        public static Params fromParameters(X9ECParameters params) {
            ECCurve curve = params.getCurve();
            ECPoint point = params.getBaseEntry().getPoint();
            if (!ECAlgorithms.isFpField(curve.getField()))
                throw new IllegalArgumentException("Binary curves are unsupported currently"); // TODO Поддержка кривых на двоичных полях

            return new Params("prime",
                    curve.getField().getCharacteristic(),
                    curve.getA().toBigInteger(),
                    curve.getB().toBigInteger(),
                    point.getAffineXCoord().toBigInteger(),
                    point.getAffineYCoord().toBigInteger(),
                    params.getN(),
                    params.getH(),
                    Hex.toString(params.getSeed())
            );
        }
    }
}
