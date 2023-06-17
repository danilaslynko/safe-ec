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
import kotlin.test.assertEquals

class AgainstSeedValidationTest {
    private val validator = SeedValidator()

    @Test
    fun testKnownCorrectSeed() {
        assert(validator.doCheck(Curve.prime192v1.parameterSpec) is Validated)
        assert(validator.doCheck(Curve.secp384r1.parameterSpec) is Validated)
        assert(validator.doCheck(Curve.secp112r1.parameterSpec) is Validated)
        assert(validator.doCheck(Curve.secp224r1.parameterSpec) is Validated)
    }

    @Test
    fun testKnownNoSeed() {
        val result = validator.doCheck(Curve.secp256k1.parameterSpec)
        assert(result is Vulnerable)
        assertEquals("No seed given, EC params are not verifiable random", (result as Vulnerable).message)
    }

    @Test
    fun testNoSeed() {
        val curve = EllipticCurve(
            ECFieldFp(BigInteger.valueOf(1000000000000)),
            BigInteger.TEN,
            BigInteger.TWO,
        )
        val result = validator.doCheck(ECParameterSpec(curve, ECPoint(BigInteger.TEN, BigInteger.TWO), BigInteger.TEN, 1))
        assert(result is Vulnerable)
        assertEquals("No seed given, EC params are not verifiable random", (result as Vulnerable).message)
    }

    @Test
    fun testIncorrectSeed() {
        val curve = EllipticCurve(
            ECFieldFp(BigInteger.valueOf(1000000000000)),
            BigInteger.TEN,
            BigInteger.TWO,
            ByteArray(20) { it.toByte() }
        )
        val result = validator.doCheck(ECParameterSpec(curve, ECPoint(BigInteger.TEN, BigInteger.TWO), BigInteger.TEN, 1))
        assert(result is Vulnerable)
        assertEquals("Params a=a and b=2 are not valid against given seed=102030405060708090a0b0c0d0e0f10111213", (result as Vulnerable).message)
    }
}