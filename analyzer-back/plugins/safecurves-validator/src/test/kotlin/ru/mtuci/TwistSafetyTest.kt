package ru.mtuci

import io.churchkey.ec.Curve
import org.junit.jupiter.api.Test
import ru.mtuci.plugins.Result.Validated
import ru.mtuci.plugins.Result.Vulnerable
import kotlin.test.assertEquals

class TwistSafetyTest {
    private val validator = TwistValidator()

    @Test
    fun testKnownOk() {
        val result = validator.doCheck(Curve.secp256k1.parameterSpec)
        assert(result is Validated)
    }

    @Test
    fun testKnownRhoNok() {
        val result = validator.doCheck(Curve.nistp224.parameterSpec)
        assert(result is Vulnerable)
        assertEquals("Curve twist is not safe to rho attacks", (result as Vulnerable).message)
    }
}
