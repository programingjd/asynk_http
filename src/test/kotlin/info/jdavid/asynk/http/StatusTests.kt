package info.jdavid.asynk.http

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class StatusTests {

  @Test
  fun testMessage() {
    assertEquals(200, Status.OK)
    assertEquals("OK", Status.message(Status.OK)?.toUpperCase())
    assertEquals(404, Status.NOT_FOUND)
    assertEquals("NOT FOUND", Status.message(Status.NOT_FOUND)?.toUpperCase())
    assertNull(Status.message(222))
  }

}
