package info.jdavid.asynk.http

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import javax.crypto.spec.SecretKeySpec

class CryptoTests {

  // Depends on SecureRandom implementation (fails on windows jdk 8 for instance).
  /*@Test*/ fun testIv() {
    val iv1 = Crypto.iv("Hardcoded seed only for testing".toByteArray())
    val iv2 = Crypto.iv("Hardcoded seed only for testing".toByteArray())
    assertNotEquals(Crypto.hex(iv1), Crypto.hex(iv2))
  }

  @Test fun testInvalid() {
    val iv1 = Crypto.unhex("a45c9012c9d76759a533df52d6db392b")
    val key1 = Crypto.secretKey(iv1)
    val key = SecretKeySpec(Crypto.unhex("3f69b3f5a5855f116ec878cec91b340d"), key1.algorithm)
    val crypted = Crypto.encrypt(key, iv1, "Super secret message".toByteArray())
    val iv2 = Crypto.unhex("a45c9012c9d76759a533d8cec91b340d")
    assertNull(Crypto.decrypt(key, iv2, crypted))
    val other = SecretKeySpec(Crypto.unhex("8ac0012c9d762f116ec878cec93a9b4f"), key1.algorithm)
    assertNull(Crypto.decrypt(other, iv1, crypted))
  }

  @Test fun testCryptEncrypt() {
    val iv1 = Crypto.unhex("a45c9012c9d76759a533df52d6db392b")
    val key1 = Crypto.secretKey(iv1)

    val key = SecretKeySpec(Crypto.unhex("3f69b3f5a5855f116ec878cec91b340d"), key1.algorithm)
    val crypted = Crypto.encrypt(key, iv1, "Super secret message".toByteArray())
    assertEquals(
      "Super secret message",
      String(Crypto.decrypt(key, iv1, crypted) ?: throw NullPointerException())
    )
  }

  @Test fun testSign() {
    val iv1 = Crypto.unhex("a45c9012c9d76759a533df52d6db392b")
    println(Crypto.sign(Crypto.secretKey(iv1), "Super secret message".toByteArray()))
  }

}
