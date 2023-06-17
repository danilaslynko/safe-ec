package ru.mtuci

import io.churchkey.ec.Curve
import org.junit.jupiter.api.Test
import ru.mtuci.plugins.Result.Validated
import ru.mtuci.plugins.Result.Vulnerable
import java.math.BigInteger
import java.security.spec.ECFieldFp
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.EllipticCurve
import java.util.*
import kotlin.test.assertEquals

class TransferSafetyValidationTest {
    @Test
    fun testKnownOk() {
        val result = TransferSafetyValidator().doCheck(Curve.nistp224.parameterSpec)
        assert(result is Validated)
    }

    @Test
    fun testKnownPairingFriendlyCurve() {
        val hex = HexFormat.of()
        val bn2_254 = ECParameterSpec(
            EllipticCurve(
                ECFieldFp(BigInteger(hex.parseHex("2523648240000001BA344D80000000086121000000000013A700000000000013"))),
                BigInteger(hex.parseHex("0000000000000000000000000000000000000000000000000000000000000000")),
                BigInteger(hex.parseHex("0000000000000000000000000000000000000000000000000000000000000002")),
            ),
            ECPoint(
                BigInteger(hex.parseHex("2523648240000001BA344D80000000086121000000000013A700000000000012")),
                BigInteger(hex.parseHex("0000000000000000000000000000000000000000000000000000000000000001"))
            ),
            BigInteger(hex.parseHex("2523648240000001BA344D8000000007FF9F800000000010A10000000000000D")),
            1
        )
        val result = TransferSafetyValidator().doCheck(bn2_254)
        assert(result is Vulnerable)
        assertEquals("Curve is vulnerable to MOV-attacks, embedding degree is too low (12)", (result as Vulnerable).message)
    }
}
