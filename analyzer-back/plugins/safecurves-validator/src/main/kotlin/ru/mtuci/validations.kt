package ru.mtuci

import de.tilman_neumann.jml.precision.Scale
import de.tilman_neumann.jml.roots.SqrtReal.sqrt
import de.tilman_neumann.jml.transcendental.Ln.ln
import de.tilman_neumann.jml.transcendental.Ln.ln2
import de.tilman_neumann.jml.transcendental.Pi
import io.churchkey.asn1.Oid
import io.churchkey.ec.Curve
import org.slf4j.LoggerFactory
import ru.mtuci.plugins.Plugin
import ru.mtuci.plugins.Priority
import ru.mtuci.plugins.Request
import ru.mtuci.plugins.Request.Params.Supplementary
import ru.mtuci.plugins.Result
import ru.mtuci.plugins.Result.*
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.security.MessageDigest
import java.security.spec.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private fun bi(int: Int) = BigInteger.valueOf(int.toLong())
private fun bi(bytes: ByteArray) = BigInteger(1, bytes)

private infix fun BigInteger.eq(other: BigInteger) = this.compareTo(other) == 0
private infix fun BigInteger.ne(other: BigInteger) = !this.eq(other)

private val `-1` = bi(-1)
private val `0` = bi(0)
private val `1` = bi(1)
private val `2` = bi(2)
private val `4` = bi(4)
private val `27` = bi(27)
private val `100` = bi(100)
private val `2^200` = bi(2).pow(200)

private val cache = ConcurrentHashMap<Pair<Class<*>, CacheKey>, Result>()

private val log = LoggerFactory.getLogger("ru.mtuci.SafecurvesValidator")

private const val FIELD2M_VULN = "SafeECVulnerability-Field2mNotAllowed"
private const val P_NOT_PRIME = "SafeECVulnerability-PnotPrime"
private const val WRONG_CURVE = "SafeECVulnerability-WrongCurve"
private const val BASE_POINT_NOT_ON_CURVE = "SafeECVulnerability-BasePointNotOnCurve"
private const val NO_SEED = "SafeECVulnerability-NoSeed"
private const val WRONG_SEED = "SafeECVulnerability-WrongSeed"
private const val ORDER_NOT_PRIME = "SafeECVulnerability-PohligHellman-OrderNotPrime"
private const val ORDER_TOO_LOW = "SafeECVulnerability-RhoPollard-OrderTooLow"
private const val LOW_EMBEDDING_DEGREE = "SafeECVulnerability-MOVAttack-LowEmbeddingDegree"
private const val ORDER_P_GCD_NOT_1 = "SafeECVulnerability-SmartAttack-PandOrderGcdIsNot1"
private const val P_IS_ORDER_X_COFACTOR = "SafeECVulnerability-MOVAttack-PisOrderXCofactor"
private const val POSITIVE_CM_DISCRIMINANT = "SafeECVulnerability-PositiveCMDiscriminant"
private const val LOW_CM_DISCRIMINANT = "SafeECVulnerability-PositiveCMDiscriminant"
private const val LOW_TWIST_EMBEDDING_DEGREE = "SafeECVulnerability-Twist-MOVAttack-LowEmbeddingDegree"
private const val P_AND_TWIST_ORDER_GCD_1 = "SafeECVulnerability-Twist-PandTwistOrderGcdIs1"
private const val TWIST_JOINT_RHO = "SafeECVulnerability-Twist-VulnerableToJointRho"
private const val TWIST_RHO = "SafeECVulnerability-Twist-VulnerableToRho"

data class CacheKey(val params: ECParameterSpec) {
    override fun hashCode(): Int {
        return Objects.hash(params.curve, params.generator, params.cofactor, params.order)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other)
            return true

        if (javaClass != other?.javaClass)
            return false

        other as CacheKey
        return params.curve == other.params.curve &&
                params.generator == other.params.generator &&
                params.cofactor == other.params.cofactor &&
                params.order == other.params.order
    }

    override fun toString(): String {
        val field = params.curve.field
        return """
            EC parameters: {
                cofactor=${params.cofactor},
                order=${params.order},
                generator={
                    x=${params.generator.affineX}, 
                    y=${params.generator.affineY}
                },
                curve={
                    a=${params.curve.a},
                    b=${params.curve.b},
                    seed=${if (params.curve.seed == null) null else bi(params.curve.seed)},
                    ${if (field is ECFieldFp) "fp=${field.p}" else "f2m field"},
                }
            }
            """.trimMargin()
    }
}

abstract class BaseCachingValidator : Plugin {
    override fun check(params: Request.Params): Result {
        return checkCached(params.params, params.supplementary)
    }

    override fun check(named: Request.Named): Result {
        return Curve.resolve(named.name)?.let { checkCached(it.parameterSpec) }?: Undefined()
    }

    override fun check(oid: Request.OID): Result {
        return Curve.resolve(Oid.fromString(oid.oid))?.let { checkCached(it.parameterSpec) }?: Undefined()
    }

    private fun checkCached(params: ECParameterSpec, supplementary: Supplementary? = null): Result {
        return cache.computeIfAbsent(Pair(this::class.java, CacheKey(params))) {
            log.info("Checking EC curve {}", it.second)
            doCheck(params, supplementary)
        }
    }

    abstract fun doCheck(params: ECParameterSpec, supplementary: Supplementary? = null): Result;
}

class FieldValidator : BaseCachingValidator() {
    override fun priority() = Priority.HIGH
    override fun name() = "EC field validator"
    override fun description() = "Validates curve field to be prime"

    override fun doCheck(params: ECParameterSpec, supplementary: Supplementary?): Result {
        val curve = params.curve
        val field = curve.field

        return when {
            field is ECFieldF2m -> Vulnerable(FIELD2M_VULN, "SafeCurves required elliptic curves to be over prime fields")
            // При числе итераций 7 вероятность ложного срабатывания составляет около 0,009, для практических целей
            // это нормальное сочетание невысокой сложности алгоритма проверки и невысокой вероятности ошибки
            !(field as ECFieldFp).p.isProbablePrime(7) -> Vulnerable(P_NOT_PRIME, "SafeCurves requires 'p' parameter of elliptic curve to be prime, used value")
            else -> Validated()
        }
    }
}

class ABValidator : BaseCachingValidator() {
    override fun priority() = Priority.HIGH
    override fun name() = "EC a and b params validator"
    override fun description() = "Validates given a an b params to define EC"

    override fun doCheck(params: ECParameterSpec, supplementary: Supplementary?): Result {
        val curve = params.curve
        if (((curve.a.pow(3) * `4`) + (curve.b.pow(2) * `27`)) eq `0`)
            return Vulnerable(WRONG_CURVE, "4*a^3+27*b^2 is 0, this params does not define valid elliptic curve")

        if (curve.field is ECFieldFp && !isPointOnCurve(curve, params.generator))
            return Vulnerable(BASE_POINT_NOT_ON_CURVE, "Base point (x=${params.generator.affineX.toString(16)}, y=${params.generator.affineY.toString(16)}) is not on specified curve")

        return Validated()
    }
}

class OrderValidator : BaseCachingValidator() {
    override fun priority() = Priority.HIGH
    override fun name() = "Curve order validator"
    override fun description() = "Some basic validations of curve order"

    override fun doCheck(params: ECParameterSpec, supplementary: Supplementary?): Result {
        if (!params.order.isProbablePrime(7))
            return Vulnerable(ORDER_NOT_PRIME, "Curve is vulnerable to attacks using Pohlig–Hellman algorithm if it's order is not prime")

        if (params.order < `2^200`)
            return Vulnerable(ORDER_TOO_LOW, "Curves with order less than 2^200 are vulnerable to attacks using Pollard's rho algorithm")

        return Validated()
    }
}

class SeedValidator : BaseCachingValidator() {
    override fun priority() = Priority.NORMAL
    override fun name() = "EC seed validator"
    override fun description() = "Validates given seed was used to generate EC in verifiable pseudo-random way"

    override fun doCheck(params: ECParameterSpec, supplementary: Supplementary?): Result {
        val curve = params.curve
        if (curve.field !is ECFieldFp)
            return Undefined()

        if (curve.seed?.takeIf { it.isNotEmpty() } == null)
            return Vulnerable(NO_SEED, "No seed given, EC params are not verifiable random")

        val seed = prepareByteArray(curve.seed)
        val iSeed = bi(seed)
        val field = curve.field as ECFieldFp
        val g = seed.size * Byte.SIZE_BITS
        val t = field.p.bitLength()
        val s = (t - 1) / 160
        val h = t - 160 * s
        val sha1 = MessageDigest.getInstance("SHA-1")
        val H = bi(sha1.digest(seed))
        sha1.reset()
        val c0 = H and ((`1` shl h) - `1`)
        val w0 = c0 and ((`1` shl h - 1) - `1`)
        var w = arrayOf(w0.toByteArray())

        for (i in 1..s) {
            val wi = sha1.digest(prepareByteArray((iSeed + bi(i)).mod(BigInteger.TWO.pow(g)).toByteArray()))
            sha1.reset()
            w += wi
        }

        val out = ByteArrayOutputStream()
        w.forEach { out.writeBytes(it) }
        val c = bi(out.toByteArray())

        if ((curve.b.pow(2) * c - curve.a.pow(3)) % field.p != `0`)
            return Vulnerable(WRONG_SEED, "Params a=${curve.a.toString(16)} and b=${curve.b.toString(16)} are not valid against given seed=${iSeed.toString(16)}")

        return Validated()
    }
}

class TransferSafetyValidator : BaseCachingValidator() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun priority() = Priority.LOW
    override fun name() = "Transfer attacks (MOV, SMART) validator"
    override fun description() = """
        Validates curve to be not vulnerable against transfer-based attacks.
        Transfer-based attacks use ability to move points of EC to finite field 
        and then solve DLP instead of ECDLP problem. DLP problem has subexponential
        solution algorithms.
        """.trimIndent()

    override fun doCheck(params: ECParameterSpec, supplementary: Supplementary?): Result {
        val field = params.curve.field as ECFieldFp
        if ((params.order * bi(params.cofactor)) eq field.p)
            return Vulnerable(P_IS_ORDER_X_COFACTOR, "Curve is vulnerable to attacks based on additive transfer (Smart-attack)")

        val gcd = params.order.gcd(field.p)
        if (gcd ne `1`)
            return Vulnerable(ORDER_P_GCD_NOT_1, "Curve is vulnerable to MOV-attacks. GCD of order and 'p' must be 1")

        val ed = supplementary?.embeddingDegree ?: calcEmbeddingDegree(params.order, field.p, supplementary?.edFactors)

        if (ed eq `-1`)
            return Validated()

        log.debug("Embedding degree is {}", ed)
        if (((params.order - `1`) / ed) > `100`)
            return Vulnerable(LOW_EMBEDDING_DEGREE, "Curve is vulnerable to MOV-attacks, embedding degree is too low ($ed)")

        return Validated()
    }
}

class DiscriminantValidator : BaseCachingValidator() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun priority() = Priority.LOW
    override fun name() = "CM-field discriminant value validator"
    override fun description() = """
        Validated complex multiplication field discriminant for this curve is less then -2^100.
    """.trimIndent()

    override fun doCheck(params: ECParameterSpec, supplementary: Supplementary?): Result {
        val p = (params.curve.field as ECFieldFp).p
        val l = params.order
        val t = p + `1` - l * ((p + `1`).toBigDecimal() / l.toBigDecimal()).toBigInteger()
        if (l.pow(2) <= p.shl(4)) {
            log.debug("Curve order is less than 4*sqrt(p), cannot validate CM-field discriminant")
            return Undefined()
        }

        var D = t.pow(2) - `4` * p
        val baseFactors = factor(D)
        baseFactors.forEach {
            var times = it.value
            while (times >= 2) {
                D /= it.key.pow(2)
                times -= 2
            }
        }

        if (D >= `0`)
            return Vulnerable(POSITIVE_CM_DISCRIMINANT, "Discriminant is not negative")

        if (D >= `2`.pow(100).negate())
            return Vulnerable(LOW_CM_DISCRIMINANT, "CM-field discriminant is too small ($D less than 2^100)")

        return Validated()
    }
}

class TwistValidator : BaseCachingValidator() {
    private val log = LoggerFactory.getLogger(javaClass)

    private val s3 = Scale.of(3.0)
    private val bd4 = BigDecimal(4)
    private val bd100 = BigDecimal(100)
    private val pi4 = Pi.pi(s3) / bd4

    override fun priority() = Priority.LOW // twist ED computation may take a very long time
    override fun name() = "Curve twists safety validator"
    override fun description() = """
        If the original curve has p+1-t points, then any nontrivial
        quadratic twist has in turn p+1+t points. In a generic implementation,
        programmers have to be careful enough to include different checks (e.g., that the
        point sent by the other party effectively belongs to the elliptic curve, that the
        order of the point multiplied by the cofactor is not equal to O, etc.). This
        requirement checks if the elliptic curve under analysis is protected against
        attacks derived from those situations without forcing the programmer to include
        specific code to handle those situations. In the scope of their contribution, the
        authors define such a curve as a “twist-secure” curve.
    """.trimIndent()

    override fun doCheck(params: ECParameterSpec, supplementary: Supplementary?): Result {
        val field = params.curve.field as ECFieldFp
        val p = field.p
        val l = params.order
        val t = p + `1` - l * ((p + `1`).toBigDecimal() / l.toBigDecimal()).toBigInteger()
        val d = field.p + `1` + t
        val factors = factor(d)
        val twistOrder = factors.maxOf { it.key }
        if (p.gcd(twistOrder) ne `1`)
            return Vulnerable(P_AND_TWIST_ORDER_GCD_1, "Curve is not twist-safe. GCD of twist order and 'p' must be 1")

        val ed = calcEmbeddingDegree(l, p)
        if (ed ne `-1`) {
            log.debug("Embedding degree is $ed")
            if (((params.order - `1`) / ed) > `100`)
                return Vulnerable(LOW_TWIST_EMBEDDING_DEGREE, "Curve is vulnerable to MOV-attacks on twists, embedding degree is too low ($ed)")
        }

        if (ln(twistOrder.toBigDecimal().multiply(pi4), s3) / ln(bd4, s3) < bd100)
            return Vulnerable(TWIST_RHO, "Curve twist is not safe to rho attacks")

        var precompBD = `0`.toBigDecimal()
        var jointBD = l.toBigDecimal()
        val jointRhoFactors = factor(p + `1` - t)
        factors.forEach {
            jointRhoFactors.compute(it.key) { _, times ->
                when {
                    (times ?: -1) < it.value -> it.value
                    else -> times
                }
            }
        }
        jointRhoFactors.toList().map { it.toBigDecimal() }.forEach {
            if ((it + (sqrt(pi4, s3) * (jointBD / it))) < sqrt(pi4 * jointBD, s3)) {
                precompBD += it
                jointBD /= it
            }
        }
        if (ln(precompBD + sqrt(pi4 * jointBD, s3), s3) / ln2(s3) < bd100)
            return Vulnerable(TWIST_JOINT_RHO, "Curve twist is not safe to joint rho attacks")

        return Validated()
    }
}

// Проверка идет по уравнению Вейерштрасса
fun isPointOnCurve(curve: EllipticCurve, point: ECPoint): Boolean {
    val p = (curve.field as ECFieldFp).p
    return ((point.affineY.pow(2) - (point.affineX.pow(3) + (curve.a * point.affineX) + curve.b)) % p) eq `0`
}

/**
 * Удаляет незначащие нули в начале, оставляя не менее 20 байт в массиве
 */
private fun prepareByteArray(arr: ByteArray): ByteArray {
    val list = LinkedList(arr.toList())
    while (list.first.toInt() == 0 && list.size > 20)
        list.removeFirst()
    while (list.size < 20)
        list.push(0)

    return list.toByteArray()
}

private fun calcEmbeddingDegree(order: BigInteger, p: BigInteger, factors: Collection<BigInteger>? = null): BigInteger {
    var ed = order - `1`
    val primes: Collection<BigInteger> = factors ?: factor(ed).toList()
    val divisors = mutableListOf<BigInteger>()
    for (prime in primes) {
        var (_d, rem) = ed.divideAndRemainder(prime)
        if (rem eq `0`) {
            divisors.add(prime)
            while (rem eq `0`) {
                ed = _d
                val (__d, _rem) = ed.divideAndRemainder(prime); _d = __d; rem = _rem
            }
        }
    }

    if (ed ne `1`) {
        if (ed.isProbablePrime(10))
            divisors.add(ed)
        else
            return `-1`
    }

    ed = order - `1`
    val u = p % order
    for (prime in divisors) {
        var (_d, rem) = ed.divideAndRemainder(prime)
        while (rem eq `0`) {
            if (u.modPow(_d, order) ne `1`)
                break

            ed = _d
            val (__d, _rem) = ed.divideAndRemainder(prime); _d = __d; rem = _rem
        }
    }
    return ed
}
