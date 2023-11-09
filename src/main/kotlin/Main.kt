import dev.inmo.tgbotapi.extensions.api.chat.invite_links.approveChatJoinRequest
import dev.inmo.tgbotapi.extensions.api.chat.invite_links.declineChatJoinRequest
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.telegramBotWithBehaviourAndLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onChatJoinRequest
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.extensions.utils.asCommonUser
import dev.inmo.tgbotapi.extensions.utils.asPrivateChat
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.from
import dev.inmo.tgbotapi.types.ChatId
import i18n.getModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.*
import kotlin.math.abs

/**
 * XXTEA.kt comes from https://github.com/xJoeWoo/xxtea-kotlin
 */

object XXTEA {

    private const val DELTA = -0x61c88647

    @Suppress("NOTHING_TO_INLINE", "FunctionName")
    private inline fun MX(sum: Int, y: Int, z: Int, p: Int, e: Int, k: IntArray): Int {
        return (z.ushr(5) xor (y shl 2)) + (y.ushr(3) xor (z shl 4)) xor (sum xor y) + (k[p and 3 xor e] xor z)
    }

    fun encrypt(data: ByteArray, key: ByteArray): ByteArray =
        data.takeIf { it.count() != 0 }
            ?.let {
                encrypt(data.toIntArray(true), key.fixKey().toIntArray(false))
                    .toByteArray(false)
            }
            ?: data

    fun encrypt(data: String, key: ByteArray): ByteArray? =
        runCatching { encrypt(data.encodeToByteArray(throwOnInvalidSequence = true), key) }.getOrNull()

    fun encrypt(data: ByteArray, key: String): ByteArray? =
        runCatching { encrypt(data, key.encodeToByteArray(throwOnInvalidSequence = true)) }.getOrNull()

    fun encrypt(data: String, key: String): ByteArray? =
        runCatching {
            encrypt(
                data.encodeToByteArray(throwOnInvalidSequence = true),
                key.encodeToByteArray(throwOnInvalidSequence = true)
            )
        }.getOrNull()

    fun decrypt(data: ByteArray, key: ByteArray): ByteArray =
        data.takeIf { it.count() != 0 }
            ?.let {
                decrypt(data.toIntArray(false), key.fixKey().toIntArray(false))
                    .toByteArray(true)
            } ?: data

    fun decrypt(data: ByteArray, key: String): ByteArray? =
        kotlin.runCatching { decrypt(data, key.encodeToByteArray(throwOnInvalidSequence = true)) }.getOrNull()

    fun decryptToString(data: ByteArray, key: ByteArray): String? =
        kotlin.runCatching { decrypt(data, key).decodeToString(throwOnInvalidSequence = true) }.getOrNull()

    fun decryptToString(data: ByteArray, key: String): String? =
        kotlin.runCatching { decrypt(data, key)?.decodeToString(throwOnInvalidSequence = true) }.getOrNull()

    private fun encrypt(v: IntArray, k: IntArray): IntArray {
        val n = v.size - 1

        if (n < 1) {
            return v
        }
        var p: Int
        var q = 6 + 52 / (n + 1)
        var z = v[n]
        var y: Int
        var sum = 0
        var e: Int

        while (q-- > 0) {
            sum += DELTA
            e = sum.ushr(2) and 3
            p = 0
            while (p < n) {
                y = v[p + 1]
                v[p] += MX(sum, y, z, p, e, k)
                z = v[p]
                p++
            }
            y = v[0]
            v[n] += MX(sum, y, z, p, e, k)
            z = v[n]
        }
        return v
    }

    private fun decrypt(v: IntArray, k: IntArray): IntArray {
        val n = v.size - 1

        if (n < 1) {
            return v
        }
        var p: Int
        val q = 6 + 52 / (n + 1)
        var z: Int
        var y = v[0]
        var sum = q * DELTA
        var e: Int

        while (sum != 0) {
            e = sum.ushr(2) and 3
            p = n
            while (p > 0) {
                z = v[p - 1]
                v[p] -= MX(sum, y, z, p, e, k)
                y = v[p]
                p--
            }
            z = v[n]
            v[0] -= MX(sum, y, z, p, e, k)
            y = v[0]
            sum -= DELTA
        }
        return v
    }

    private fun ByteArray.fixKey(): ByteArray {
        if (size == 16) return this
        val fixedKey = ByteArray(16)

        if (size < 16) {
            copyInto(fixedKey)
        } else {
            copyInto(fixedKey, endIndex = 16)
        }
        return fixedKey
    }

    private fun ByteArray.toIntArray(includeLength: Boolean): IntArray {
        var n = if (size and 3 == 0)
            size.ushr(2)
        else
            size.ushr(2) + 1
        val result: IntArray

        if (includeLength) {
            result = IntArray(n + 1)
            result[n] = size
        } else {
            result = IntArray(n)
        }
        n = size
        for (i in 0 until n) {
            result[i.ushr(2)] = result[i.ushr(2)] or (0x000000ff and this[i].toInt() shl (i and 3 shl 3))
        }
        return result
    }

    private fun IntArray.toByteArray(includeLength: Boolean): ByteArray? {
        var n = size shl 2

        if (includeLength) {
            val m = this[size - 1]
            n -= 4
            if (m < n - 3 || m > n) {
                return null
            }
            n = m
        }
        val result = ByteArray(n)

        for (i in 0 until n) {
            result[i] = this[i.ushr(2)].ushr(i and 3 shl 3).toByte()
        }
        return result
    }
}

/*
 * Taken from http://www.miguelsanmiguel.com/2011/04/03/hideous-obfuscated-ids
 */
object PrimeEncoder {
    private const val PRIME = 253579998419723143L
    private const val PRIME_REVERSE = 1436574376327426615L

    fun encode(input: Long): Long = (input * PRIME) and Long.MAX_VALUE

    fun decode(input: Long): Long = (input * PRIME_REVERSE) and Long.MAX_VALUE
}

suspend fun main(vararg args: String) {
    if (args.size != 1){
        println("Invalid BotToken")
        return
    }

    println("BotToken: ${args[0]}")

    telegramBotWithBehaviourAndLongPolling(args[0], CoroutineScope(Dispatchers.IO)) {
        onChatJoinRequest {
            val model = getModel(it.from.asCommonUser()?.ietfLanguageCode?.code)
            val password = "${PrimeEncoder.encode(abs(it.chat.id.chatId))}|${PrimeEncoder.encode(it.from.id.chatId)}"
            val secret = "20221209"
            val fakePassword = XXTEA.encrypt(password, secret)
            val encodedPassword: String = Base64.getEncoder().encodeToString(fakePassword)
            bot.sendMessage(it.from.id, model.problem.replace("[PASSWORD]", encodedPassword))
            println("user ${it.from.id} request to join ${it.chat.id}")
        }
        onCommandWithArgs("join") { it, args ->
            val user = it.chat.asPrivateChat()!!
            val model = getModel(it.from?.asCommonUser()?.ietfLanguageCode?.code)
            println(it)
            if (args.size != 1){
                bot.sendMessage(user.id, model.usage)
                return@onCommandWithArgs
            }
            val answer = args[0]
            val splits = answer.split("|")
            if (splits.size != 2) {
                bot.sendMessage(user.id, model.usage)
                return@onCommandWithArgs
            }
            val chatId = try {
                -PrimeEncoder.decode(splits[0].toLong())
            } catch (_: Throwable) {
                bot.sendMessage(user.id, model.usage)
                return@onCommandWithArgs
            }
            val password = splits[1]
            val expectedPassword = PrimeEncoder.encode(user.id.chatId).toString()
            if (expectedPassword == password) {
                bot.approveChatJoinRequest(ChatId(chatId), user.id)
                bot.sendMessage(it.chat, model.correct)
                println("user ${user.id} joined $chatId")
            } else {
                bot.declineChatJoinRequest(ChatId(chatId), user.id)
                bot.sendMessage(user.id, model.incorrect)
                println("user ${user.id} join $chatId failed")
            }
        }
    }.second.join()
}
