package com.example.todolist.data

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Salted, iterated SHA-256 password hashing (local-only storage for now).
 * No plaintext passwords are ever persisted.
 */
object PasswordHasher {

    private const val ITERATIONS = 10_000
    private const val SALT_BYTES = 16
    private val random = SecureRandom()

    /** Returns (saltHex, hashHex). */
    fun hash(password: String): Pair<String, String> {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val hash = derive(password.toByteArray(Charsets.UTF_8), salt)
        return salt.toHex() to hash.toHex()
    }

    fun verify(password: String, saltHex: String, hashHex: String): Boolean {
        val salt = saltHex.hexToBytes()
        val expected = hashHex.hexToBytes()
        val actual = derive(password.toByteArray(Charsets.UTF_8), salt)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: ByteArray, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        var current = digest.digest(salt + password)
        val result = current.copyOf()
        repeat(ITERATIONS - 1) {
            current = digest.digest(current + password)
            for (i in result.indices) {
                result[i] = (result[i].toInt() xor current[i].toInt()).toByte()
            }
        }
        return result
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
