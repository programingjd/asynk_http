package info.jdavid.asynk.http

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MethodTests {

  @Test
  fun testMethods() {
    listOf(
      Method.HEAD, Method.GET, Method.DELETE, Method.POST, Method.PUT, Method.PATCH, Method.OPTIONS
    ).forEach { method ->
      assertEquals(method, Method.from(method.javaClass.simpleName))
      assertEquals(method, Method.from(method.toString()))
    }
    assertEquals("TEST", Method.Custom("TEST").toString())
    try {
      Method.from("TEST")
      fail<Nothing>()
    }
    catch (ignore: RuntimeException) {}
  }

}
