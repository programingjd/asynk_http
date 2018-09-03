package info.jdavid.asynk.http

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer

class HexTests {

  @Test fun testHexBytes() {
    assertEquals("", Crypto.hex(byteArrayOf()))
    assertEquals("0102030c0dfae8ff", Crypto.hex(byteArrayOf(
      0x01, 0x02, 0x03, 0x0c, 0x0D, 0xFa.toByte(), 0xE8.toByte(), 0xFF.toByte()
    )))
  }

  @Test fun testHexByteBuffer() {
    assertEquals("", Crypto.hex(ByteBuffer.allocate(8).flip() as ByteBuffer))
    assertEquals("0102030c0dfae8ff", Crypto.hex(ByteBuffer.wrap(byteArrayOf(
      0x01, 0x02, 0x03, 0x0c, 0x0D, 0xFa.toByte(), 0xE8.toByte(), 0xFF.toByte()
    ))))
  }

  @Test fun testUnhexToBytes() {
    assertEquals(0, Crypto.unhex("").size)
    val ref = byteArrayOf(
      0x01, 0x02, 0x03, 0x0c, 0x0D, 0xFa.toByte(), 0xE8.toByte(), 0xFF.toByte()
    )
    val bytes = Crypto.unhex("0102030C0dfAe8fF")
    assertEquals(ref.size, bytes.size)
    for (i in 0 until ref.size) assertEquals(ref[i], bytes[i])
  }

  @Test fun testUnhexToByteBuffer() {
    assertEquals(0, Crypto.unhex("").size)
    val ref = byteArrayOf(
      0xb0.toByte(), 0xb1.toByte(), 0xb2.toByte(),
      0x01, 0x02, 0x03, 0x0c, 0x0D, 0xFa.toByte(), 0xE8.toByte(), 0xFF.toByte()
    )
    val buffer = ByteBuffer.allocate(ref.size + 3)
    buffer.put(0xb0.toByte())
    buffer.put(0xb1.toByte())
    buffer.put(0xb2.toByte())
    Crypto.unhex(buffer,"0102030C0dfAe8fF")
    buffer.flip()
    assertEquals(ref.size, buffer.limit())
    for (i in 0 until ref.size) assertEquals(ref[i], buffer.get())
  }

}
