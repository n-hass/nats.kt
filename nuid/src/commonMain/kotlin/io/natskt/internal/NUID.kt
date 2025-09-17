package io.natskt.internal

import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.properties.Delegates

private val DIGITS = ('0'..'9') + ('A'..'Z') + ('a'..'z')

private const val BASE = 62
private const val PRE_LEN = 12
private const val SEQ_LEN = 10
private const val MAX_SEQ = 839299365868340224L // base^seqLen == 62^10

private const val MIN_INC = 33L
private const val MAX_INC = 333L
private const val TOTAL_LEN = PRE_LEN + SEQ_LEN

public open class NUID {
    public companion object Default : NUID()

    public val pre: CharArray = CharArray(PRE_LEN)
    public var seq: Long by Delegates.notNull()
    public var inc: Long by Delegates.notNull()

    init {
        resetSequential()
        randomizePrefix()
    }

    public fun next(): String {
        seq += inc
        if (seq >= MAX_SEQ) {
            randomizePrefix()
            resetSequential()
        }

        val b = CharArray(TOTAL_LEN)
        pre.copyInto(b, 0, 0, PRE_LEN)

        var l = seq
        for (i in b.lastIndex downTo PRE_LEN) {
            b[i] = DIGITS[(l % BASE).toInt()]
            l /= BASE
        }

        return b.concatToString()
    }

    public fun nextSequence(): String {
        seq += inc
        if (seq >= MAX_SEQ) {
            randomizePrefix()
            resetSequential()
        }

        val b = CharArray(SEQ_LEN)
        var l = seq
        for (i in b.lastIndex downTo 0) {
            b[i] = DIGITS[(l % BASE).toInt()]
            l /= BASE
        }

        return b.concatToString()
    }

    private fun resetSequential() {
        seq = CryptographyRandom.nextLong(MAX_SEQ)
        inc = CryptographyRandom.nextLong(MIN_INC, MAX_INC)
    }

    private fun randomizePrefix() {
        val bytes = CryptographyRandom.nextBytes(PRE_LEN)
        for (i in pre.indices) pre[i] = DIGITS[(bytes[i].toInt() and 0xFF) % BASE]
    }

    override fun toString(): String = "NUID(seq=$seq, inc=$inc)"
}
