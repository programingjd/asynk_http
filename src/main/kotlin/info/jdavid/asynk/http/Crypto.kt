@file:Suppress("TooManyFunctions")

package info.jdavid.asynk.http

import com.codahale.aesgcmsiv.AEAD
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.Key
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac

object Crypto {

  private const val HMAC_SHA256 = "HmacSHA256"

  private const val AES = "AES"
  private const val ZERO = "0"

  init {
    if (!algorithmIsAvailable(AES)) throw RuntimeException("No suitable algorithm found.")
    if (!macIsAvailable(HMAC_SHA256)) throw RuntimeException("No suitable mac found.")
  }

  private fun macIsAvailable(algorithm: String): Boolean {
    return try {
      Mac.getInstance(algorithm)
      true
    }
    catch (ignore: GeneralSecurityException) {
      false
    }
  }

  private fun algorithmIsAvailable(algorithm: String): Boolean {
    return try {
      KeyGenerator.getInstance(algorithm)
      true
    }
    catch (ignore: GeneralSecurityException) {
      false
    }
  }

  /**
   * Signs the payload with the specified key.
   * @param key the key.
   * @param bytes the payload.
   * @return the signature (hex).
   */
  fun sign(key: Key, bytes: ByteArray) = Mac.getInstance(HMAC_SHA256).let {
    it.init(key)
    hex(it.doFinal(bytes))
  }

  /**
   * Encrypts the payload with the specified key and iv.
   * @param key the key.
   * @param iv the initialization vector (should be 32/64 bytes long).
   * @param bytes the payload.
   * @return the encrypted bytes.
   */
  fun encrypt(key: Key, iv: ByteArray, bytes: ByteArray): ByteArray = AEAD(key.encoded).seal(bytes, iv)

  /**
   * Decrypts the payload with the specified key and iv.
   * @param key the key.
   * @param iv the initialization vector (should be 32/64 bytes long).
   * @param crypted the payload.
   * @return the decrypted bytes.
   */
  fun decrypt(key: Key, iv: ByteArray, crypted: ByteArray): ByteArray? =
    AEAD(key.encoded).open(crypted, iv).let {
      if (it.isPresent) it.get() else null
    }

  /**
   * Creates an initialization vector.
   * @param seed the initialization seed.
   */
  fun iv(seed: ByteArray) = ByteArray(32).apply {
    SecureRandom(seed).nextBytes(this)
  }

  /**
   * Creates a secret key from an iv.
   * @param iv the initialization vector.
   * @param the new key.
   */
  // Works for AEAD but also for HMAC
  fun secretKey(iv: ByteArray): Key = KeyGenerator.getInstance(AES).let {
    it.init(SecureRandom(iv))
    it.generateKey()
  }

  /**
   * Gets the hexadecimal representation of a number.
   * @param n the number.
   * @return the hex string.
   */
  fun hex(n: BigInteger): String {
    val s = n.toString(16)
    return if (s.length % 2 == 0) s else ZERO + s
  }

  /**
   * Gets the hexadecimal representation of a payload.
   * @param bytes the payload.
   * @return the hex string.
   */
  fun hex(bytes: ByteArray): String {
    val chars = CharArray(bytes.size * 2)
    var i = 0
    for (b in bytes) {
      chars[i++] = Character.forDigit(b.toInt().shr(4).and(0xf), 16)
      chars[i++] = Character.forDigit(b.toInt().and(0xf), 16)
    }
    return String(chars)
  }

  /**
   * Gets the hexadecimal representation of a payload.
   * @param buffer the payload.
   * @return the hex string.
   */
  fun hex(buffer: ByteBuffer): String {
    val chars = CharArray(buffer.remaining() * 2)
    var i = 0
    while (buffer.remaining() > 0) {
      val b = buffer.get()
      chars[i++] = Character.forDigit(b.toInt().shr(4).and(0xf), 16)
      chars[i++] = Character.forDigit(b.toInt().and(0xf), 16)
    }
    return String(chars)
  }

  /**
   * Gets a serie of bytes from its hexadecimal representation.
   * @param hex the hex string.
   * @return the payload.
   */
  fun unhex(hex: String): ByteArray {
    return ByteArray(hex.length / 2) {
      (hexDigit(hex[it*2]).shl(4) + hexDigit(hex[it*2+1])).toByte()
    }
  }

  /**
   * Gets a serie of bytes from its hexadecimal representation.
   * @param buffer the buffer to write the bytes to.
   * @param hex the hex string.
   * @return the payload.
   */
  fun unhex(buffer: ByteBuffer, hex: String) {
    for (i in 0 until hex.length / 2) {
      buffer.put((hexDigit(hex[i*2]).shl(4) + hexDigit(hex[i*2+1])).toByte())
    }
  }

  private fun hexDigit(c: Char): Int {
    if (c <= '9') return c - '0'
    if (c <= 'F') return c - 'A' + 10
    if (c <= 'f') return c - 'a' + 10
    throw IllegalArgumentException("Unexpected hex digit: ${c}")
  }

}
