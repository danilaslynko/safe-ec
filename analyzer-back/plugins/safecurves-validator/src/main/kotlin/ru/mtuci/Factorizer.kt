package ru.mtuci

import de.tilman_neumann.jml.factor.CombinedFactorAlgorithm
import de.tilman_neumann.jml.factor.base.UnsafeUtil
import de.tilman_neumann.util.SortedMultiset
import de.tilman_neumann.util.SortedMultiset_BottomUp
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.slf4j.LoggerFactory
import java.io.File
import java.math.BigInteger
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private class LRUCache<K, V> : LinkedHashMap<K, V>() {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
        return size > 100
    }
}

private val sync = ReentrantReadWriteLock()
private val cache = LRUCache<BigInteger, SortedMultiset<BigInteger>>()
private val cacheDir = File("safecurves/primes").absoluteFile
private val cacheLock = cacheDir.resolve("cache.lock")

fun main(vararg args: String) {
    LogManager.getLogger(UnsafeUtil::class.java).level = Level.OFF
    val factorizer = CombinedFactorAlgorithm(6)
    val factors = factorizer.factor(BigInteger(args[0]))
    val result = factors.map { "${it.key}^${it.value}" }.joinToString("+")
    println(result)
}

private fun cache(bi: BigInteger, factors: SortedMultiset<BigInteger>) = sync.write {
    cache[bi] = factors
    if (!cacheDir.exists())
        cacheDir.mkdirs()

    try {
        FileChannel.open(cacheLock.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).lock().use {
            val primesDir = cacheDir.resolve(bi.toString())
            if (primesDir.exists())
                primesDir.deleteRecursively()

            if (!primesDir.exists())
                primesDir.mkdirs()

            factors.forEach { (prime, times) ->
                val primeFile = primesDir.resolve(prime.toString())
                primeFile.createNewFile()
                primeFile.writer().use { it.write("$times") }
            }
        }
    } catch (e: Exception) {
        log.error("Cannot write cache to file system", e)
    }
}

private fun fromCache(bi: BigInteger, factors: SortedMultiset<BigInteger>) = sync.read {
    val cached = cache[bi]
    if (!cached.isNullOrEmpty()) {
        factors.addAll(cached)
        return
    }

    fromDir(bi, factors)
}

private fun fromDir(bi: BigInteger, factors: SortedMultiset<BigInteger>) {
    try {
        FileChannel.open(cacheLock.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).lock().use {
            if (cacheDir.exists() && !cacheDir.isDirectory)
                cacheDir.deleteRecursively()

            if (!cacheDir.exists())
                cacheDir.mkdirs()

            val primesDir = cacheDir.resolve(bi.toString())
            if (!primesDir.exists() || !primesDir.isDirectory)
                return

            val primes = primesDir.list()
            for (prime in primes!!) {
                val primeBi = BigInteger(prime)
                val absBi = bi.abs();
                if ((absBi.mod(primeBi.abs())).compareTo(BigInteger.ZERO) != 0) {
                    factors.clear()
                    primesDir.deleteRecursively()
                    return
                }

                val times = Integer.parseInt(primesDir.resolve(prime).readText())
                if (primeBi.signum() == -1) {
                    if (primeBi.abs().compareTo(BigInteger.ONE) != 0 || bi.signum() != -1 || times != 1) {
                        factors.clear()
                        primesDir.deleteRecursively()
                        return
                    }
                } else {
                    var localBi = absBi;
                    var realTimes = 0
                    while (localBi.mod(primeBi).compareTo(BigInteger.ZERO) == 0) {
                        realTimes++
                        localBi /= primeBi
                    }

                    if (realTimes != times) {
                        factors.clear()
                        primesDir.deleteRecursively()
                        return
                    }
                }

                factors[primeBi] = times
            }
        }
    } catch (e: Exception) {
        log.error("Unable to find cached factors", e)
        factors.clear()
    }
}

private val log = LoggerFactory.getLogger("ru.mtuci.Factor")

class Factorizer

fun factor(integer: BigInteger): SortedMultiset<BigInteger> {
    val factors = SortedMultiset_BottomUp<BigInteger>()
    fromCache(integer, factors)
    if (!factors.isEmpty())
        return factors

    val timeout = Integer.getInteger("factor.timeout", 60).toLong()
    val javaHome = System.getProperty("java.home")
    val javaBin = javaHome + File.separator + "bin" + File.separator + "java"
    val clazz = Factorizer::class.java
    val currentJar = clazz.protectionDomain.codeSource.location.file
    val classpath = System.getProperty("java.class.path") +
            if (System.getProperty("os.name").lowercase(Locale.getDefault()).contains("win"))
                ";$currentJar"
            else
                ":$currentJar"

    val command = listOf(
            javaBin,
//       "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
            "-cp",
            classpath,
            "ru.mtuci.FactorizerKt",
            integer.toString()
    )
    val processBuilder = ProcessBuilder(command)

    log.info("Factorizing $integer")
    val process = processBuilder.start()
    var result = ""
    val outputCollected = CountDownLatch(1)
    val outputCollector = Thread {
        try {
            try {
                val error = process.errorStream.reader().readText();
                if (error.isNotEmpty())
                    log.error("\n{}", error);
            } catch (_: Throwable) {
            }
            result = process.inputStream.reader().readText()
        } catch (e: Throwable) {
            log.error("Cannot factor number $integer", e)
        } finally {
            outputCollected.countDown()
        }
    }
    outputCollector.start()
    val exitValue = if (timeout > 0) {
        if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            log.info("Factorizer killed")
        }

        process.exitValue()
    } else {
        process.waitFor()
    }

    outputCollected.await()
    if (exitValue != 0 || result.isEmpty())
        throw RuntimeException("Factorizer finished with error, output is '$result'")

    result.trim().split("+").forEach {
        val split = it.split("^")
        val factor = BigInteger(split[0])
        val times = Integer.parseInt(split[1])
        factors.add(factor, times)
    }

    cache(integer, factors)
    log.info("Factor result $factors")
    return factors
}
