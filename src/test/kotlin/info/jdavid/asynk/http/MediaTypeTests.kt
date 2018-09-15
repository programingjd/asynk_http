package info.jdavid.asynk.http

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

class MediaTypeTests {

  @Test
  fun testFromUri() {
    assertEquals(MediaType.JSON, MediaType.fromUri("/test.json"))
    assertEquals(MediaType.JSON, MediaType.fromUri("/test.json#hash"))
    assertEquals(MediaType.DIRECTORY, MediaType.fromUri("/"))
    assertNull(MediaType.fromUri("/test.abc"))
  }

  @Test
  fun testFromFile() {
    assertEquals(MediaType.JSON, MediaType.fromFile(File("test.json")))
    assertEquals(MediaType.DIRECTORY, MediaType.fromFile(File(".")))
    assertNull(MediaType.fromFile(File("/test.abc")))
  }

}
