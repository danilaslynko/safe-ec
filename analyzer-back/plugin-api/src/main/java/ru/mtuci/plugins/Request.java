package ru.mtuci.plugins;

import java.math.BigInteger;
import java.security.spec.ECParameterSpec;
import java.util.List;

public interface Request {
    record OID(String oid) implements Request {
        @Override
        public Result accept(Plugin plugin) {
            return plugin.check(this);
        }
    }
    record Named(String name) implements Request {
        @Override
        public Result accept(Plugin plugin) {
            return plugin.check(this);
        }
    }
    record Params(ECParameterSpec params, Supplementary supplementary) implements Request {
        public record Supplementary(
                BigInteger embeddingDegree,
                List<BigInteger> edFactors
        ) {}

        @Override
        public Result accept(Plugin plugin) {
            return plugin.check(this);
        }
    }

    Result accept(Plugin plugin);
}
