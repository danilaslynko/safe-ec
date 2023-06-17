package ru.mtuci

import io.churchkey.asn1.Oid
import io.churchkey.ec.Curve
import ru.mtuci.plugins.Plugin
import ru.mtuci.plugins.Priority
import ru.mtuci.plugins.Request
import ru.mtuci.plugins.Request.OID
import ru.mtuci.plugins.Result
import ru.mtuci.plugins.Result.Undefined
import ru.mtuci.plugins.Result.Validated
import ru.mtuci.plugins.Result.Vulnerable

val list = listOf(
    Curve.nistp224,
    Curve.brainpoolp256t1,
    Curve.frp256v1,
    Curve.nistp256,
    Curve.secp256k1,
    Curve.brainpoolp384t1,
    Curve.nistp384
)

class KnownVulnerableListValidator : Plugin {
    override fun priority() = Priority.HIGH
    override fun name() = "Prevalidated curves list"
    override fun description() = "Checks curves against list of already known vulnerable curves"

    override fun check(oid: OID): Result {
        return check(Curve.resolve(Oid.fromString(oid.oid)))
    }

    override fun check(named: Request.Named): Result {
        return check(Curve.resolve(named.name))
    }
}

fun check(c: Curve?): Result {
    return if (c == null)
        Undefined()
    else if (list.contains(c))
        Vulnerable("Usage of curve " + c.getName() + " is insecure")
    else
        Validated()
}
