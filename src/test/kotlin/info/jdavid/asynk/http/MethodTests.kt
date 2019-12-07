package info.jdavid.asynk.http

import info.jdavid.asynk.http.internal.Http
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer

class MethodTests {

  @Test
  fun testMethods() {
    listOf(
      Method.HEAD, Method.GET, Method.DELETE, Method.POST, Method.PUT, Method.PATCH, Method.OPTIONS
    ).forEach { method ->
      assertEquals(method, Method.from(method.javaClass.simpleName))
      assertEquals(method, Method.from(method.toString()))
    }
    val method = Method.Custom("TEST")
    assertEquals(method, Method.from(method.toString()))
  }

  @Test
  fun testGet() {
    val buffer = ByteBuffer.allocate(256)
    buffer.put("GET http://example.com HTTP/1.1\r\n\r\n".toByteArray())
    buffer.rewind()
    assertEquals(Method.GET, Http.method(buffer))
  }

  @Test
  fun testOptions() {
    val buffer = ByteBuffer.allocate(256)
    buffer.put("OPTIONS http://example.com HTTP/1.1\r\n\r\n".toByteArray())
    buffer.rewind()
    assertEquals(Method.OPTIONS, Http.method(buffer))
  }

  @Test
  fun testCustomMethod() {
    val buffer = ByteBuffer.allocate(256)
    buffer.put("GO http://example.com HTTP/1.1\r\n\r\n".toByteArray())
    buffer.rewind()
    assertEquals("GO", Http.method(buffer).toString())
  }

  @Test
  fun testMethodTooLong() {
    val buffer = ByteBuffer.allocate(256)
    buffer.put("TOOLONGMETHOD http://example.com HTTP/1.1\r\n\r\n".toByteArray())
    buffer.rewind()
    assertNull(Http.method(buffer))
  }

  @Test
  fun testInvalid() {
    val buffer = ByteBuffer.allocate(256)
    buffer.put("A_B http://example.com HTTP/1.1\r\n\r\n".toByteArray())
    buffer.rewind()
    assertNull(Http.method(buffer))
  }

}
