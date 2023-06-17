package ru.mtuci

import io.churchkey.ec.Curve
import org.junit.jupiter.api.Test
import ru.mtuci.plugins.Result.Validated
import ru.mtuci.plugins.Result.Vulnerable
import kotlin.test.assertEquals

class DiscriminantValidationTest {
    private val validator = DiscriminantValidator()

    @Test
    fun testKnownOk() {
        val result = validator.doCheck(Curve.nistp256.parameterSpec)
        assert(result is Validated)
    }

    @Test
    fun testKnownNok() {
        val result = validator.doCheck(Curve.secp256k1.parameterSpec)
        assert(result is Vulnerable)
        assertEquals("CM-field discriminant is too small (-3 less than 2^100)", (result as Vulnerable).message)
    }
}