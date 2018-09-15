package info.jdavid.asynk.http

import info.jdavid.asynk.http.internal.Http
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import kotlinx.coroutines.experimental.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel

class HttpTests {

  @Test
  fun testRequestLine() {
    ByteBuffer.wrap("GET /test/request/line HTTP/1.1\r\n".toByteArray()).apply {
      assertEquals(Method.GET, Http.method(this))
      assertEquals("/test/request/line", Http.uri(this))
    }
    ByteBuffer.wrap("PUT / HTTP/1.1\r\n".toByteArray()).apply {
      assertEquals(Method.PUT, Http.method(this))
      assertEquals("/", Http.uri(this))
    }
    ByteBuffer.wrap(
      "POST /some/longer/request/line/for/testing?with=a&query=and#hash HTTP/1.1\r\n".toByteArray()
    ).apply {
      assertEquals(Method.POST, Http.method(this))
      assertEquals("/some/longer/request/line/for/testing?with=a&query=and#hash", Http.uri(this))
    }
  }

  @Test
  fun testResponseLine() {
    ByteBuffer.wrap("HTTP/1.1 200 OK\r\n".toByteArray()).apply {
      assertEquals(Http.Version.HTTP_1_1, Http.httpVersion(this))
      assertEquals(Status.OK, Http.status(this))
    }
    ByteBuffer.wrap("HTTP/1.0 404 NOT FOUND\r\n".toByteArray()).apply {
      assertEquals(Http.Version.HTTP_1_0, Http.httpVersion(this))
      assertEquals(Status.NOT_FOUND, Http.status(this))
    }
  }

  @Test
  fun testHeaders() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers()
            aRead(buffer, 1000L)
            buffer.flip()
            Http.headers(this, buffer, headers)
            assertEquals(4, headers.lines.size)
            assertEquals("a2", headers.value("A"))
            assertEquals("b1", headers.value("B"))
            assertEquals("c1; c2", headers.value("C"))
            assertEquals(2, headers.values("A").size)
            assertEquals("a1", headers.values("A")[0])
            assertEquals("a2", headers.values("A")[1])
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            aWrite(ByteBuffer.wrap("A:a1\r\nB: b1\r\nC: c1; c2\r\nA: a2\r\n\r\n".toByteArray()), 1000L)
          }
        }
        server.await().close()
        client.await().close()
      }
    }
  }

  @Test
  fun testHeadersSlowConnection() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers()
            aRead(buffer, 1000L)
            buffer.flip()
            Http.headers(this, buffer, headers)
            assertEquals(4, headers.lines.size)
            assertEquals("a2", headers.value("A"))
            assertEquals("b1", headers.value("B"))
            assertEquals("c1; c2", headers.value("C"))
            assertEquals(2, headers.values("A").size)
            assertEquals("a1", headers.values("A")[0])
            assertEquals("a2", headers.values("A")[1])
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            val arr = "A:a1\r\nB: b1\r\nC: c1; c2\r\nA: a2\r\n\r\n".toByteArray()
            val buf = ByteBuffer.allocateDirect(2)
            for (b in arr) {
              buf.put(b)
              if (buf.position() == 2) {
                buf.flip()
                aWrite(buf, 250L)
                buf.flip()
              }
              delay(100L)
            }
            if (buf.position() > 0) {
              buf.flip()
              aWrite(buf, 250L)
            }
          }
        }
        server.await().close()
        client.await().close()
      }
    }
  }

  @Test
  fun testBodyHttp10BodyNotAllowed() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers().add(Headers.CONTENT_TYPE, MediaType.TEXT)
            aRead(buffer, 1000L)
            buffer.flip()
            assertEquals(
              Status.BAD_REQUEST,
              Http.body(this, Http.Version.HTTP_1_0, buffer, false, false, headers, null)
            )
          }
        }
        launch {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            aWrite(ByteBuffer.wrap("this is the body.".toByteArray()), 1000L)
            close()
          }
        }
        server.await().close()
      }
    }
  }

  @Test
  fun testBodyHttp11BodyNotAllowed() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.CONTENT_LENGTH,"17")
            aRead(buffer, 1000L)
            buffer.flip()
            assertEquals(
              Status.BAD_REQUEST,
              Http.body(this, Http.Version.HTTP_1_1, buffer, false, false, headers, null)
            )
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            aWrite(ByteBuffer.wrap("this is the body.".toByteArray()), 1000L)
          }
        }
        server.await().close()
        client.await().close()
      }
    }
  }

  @Test
  fun testBodyHttp10() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers().add(Headers.CONTENT_TYPE, MediaType.TEXT)
            aRead(buffer, 1000L)
            buffer.flip()
            assertNull(
              Http.body(this, Http.Version.HTTP_1_0, buffer, true, false, headers, null)
            )
            assertEquals("this is the body.",
                         String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
          }
        }
        launch {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            aWrite(ByteBuffer.wrap("this is the body.".toByteArray()), 1000L)
            close()
          }
        }
        server.await().close()
      }
    }
  }

  @Test
  fun testBodyHttp10SlowConnection() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers().add(Headers.CONTENT_TYPE, MediaType.TEXT)
            aRead(buffer, 1000L)
            buffer.flip()
            assertNull(
              Http.body(this, Http.Version.HTTP_1_0, buffer, true, false, headers, null)
            )
            assertEquals("this is the body.",
                         String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
          }
        }
        launch {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            val arr = "this is the body.".toByteArray()
            val buf = ByteBuffer.allocateDirect(2)
            for (b in arr) {
              buf.put(b)
              if (buf.position() == 2) {
                buf.flip()
                aWrite(buf, 250L)
                buf.flip()
              }
              delay(100L)
            }
            if (buf.position() > 0) {
              buf.flip()
              aWrite(buf, 250L)
            }
            close()
          }
        }
        server.await().close()
      }
    }
  }

  @Test
  fun testBodyHttp11() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.CONTENT_LENGTH, "17")
            aRead(buffer, 1000L)
            buffer.flip()
            assertNull(
              Http.body(this, Http.Version.HTTP_1_1, buffer, true, false, headers, null)
            )
            assertEquals("this is the body.",
                         String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            aWrite(ByteBuffer.wrap("this is the body.".toByteArray()), 1000L)
          }
        }
        server.await().close()
        client.await().close()
      }
    }
  }

  @Test
  fun testBodyHttp11SlowConnection() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.CONTENT_LENGTH, "17")
            aRead(buffer, 1000L)
            buffer.flip()
            assertNull(
              Http.body(this, Http.Version.HTTP_1_1, buffer, true, false, headers, null)
            )
            assertEquals("this is the body.",
                         String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            val arr = "this is the body.".toByteArray()
            val buf = ByteBuffer.allocateDirect(2)
            for (b in arr) {
              buf.put(b)
              if (buf.position() == 2) {
                buf.flip()
                aWrite(buf, 250L)
                buf.flip()
              }
              delay(100L)
            }
            if (buf.position() > 0) {
              buf.flip()
              aWrite(buf, 250L)
            }
          }
        }
        server.await().close()
        client.await().close()
      }
    }
  }

  @Test
  fun testBodyHttp11WrongContentLength() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.CONTENT_LENGTH, "16")
            aRead(buffer, 1000L)
            buffer.flip()
            assertEquals(
              Status.BAD_REQUEST,
              Http.body(this, Http.Version.HTTP_1_1, buffer, true, false, headers, null)
            )
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            aWrite(ByteBuffer.wrap("this is the body.".toByteArray()), 1000L)
          }
        }
        server.await().close()
        client.await().close()
      }
    }
  }

  @Test
  fun testBodyTooLargeHttp10() {
    val buffer = ByteBuffer.allocateDirect(48)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT)
            aRead(buffer, 1000L)
            buffer.flip()
            assertEquals(
              Status.PAYLOAD_TOO_LARGE,
              Http.body(this, Http.Version.HTTP_1_0, buffer, true, false, headers, null)
            )
          }
        }
        launch {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            aWrite(ByteBuffer.wrap("this is a body that is too large to fit in the buffer.".toByteArray()), 1000L)
            close()
          }
        }
        server.await().close()
      }
    }
  }

  @Test
  fun testBodyTooLargeHttp11() {
    val buffer = ByteBuffer.allocateDirect(48)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.CONTENT_LENGTH, "54")
            aRead(buffer, 1000L)
            buffer.flip()
            assertEquals(
              Status.PAYLOAD_TOO_LARGE,
              Http.body(this, Http.Version.HTTP_1_1, buffer, true, false, headers, null)
            )
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            aWrite(ByteBuffer.wrap("this is a body that is too large to fit in the buffer.".toByteArray()), 1000L)
          }
        }
        server.await().close()
        client.await().close()
      }
    }
  }

  @Test
  fun testBodyHttp10UnsupportedCompression() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.CONTENT_ENCODING, "br")
            aRead(buffer, 1000L)
            buffer.flip()
            assertEquals(
              Status.UNSUPPORTED_MEDIA_TYPE,
              Http.body(this, Http.Version.HTTP_1_0, buffer, true, false, headers, null)
            )
          }
        }
        launch {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            aWrite(ByteBuffer.wrap("this is the body.".toByteArray()), 1000L)
            close()
          }
        }
        server.await().close()
      }
    }
  }

  @Test
  fun testBodyHttp11UnsupportedCompression() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.CONTENT_LENGTH, "17").
              add(Headers.CONTENT_ENCODING, "br")
            aRead(buffer, 1000L)
            buffer.flip()
            assertEquals(
              Status.UNSUPPORTED_MEDIA_TYPE,
              Http.body(this, Http.Version.HTTP_1_1, buffer, true, false, headers, null)
            )
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            aWrite(ByteBuffer.wrap("this is the body.".toByteArray()), 1000L)
          }
        }
        server.await().close()
        client.await().close()
      }
    }
  }

  @Test
  fun testBodyChunked() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.TRANSFER_ENCODING, "chunked")
            aRead(buffer, 1000L)
            buffer.flip()
            assertNull(
              Http.body(this, Http.Version.HTTP_1_1, buffer, true, false, headers, null)
            )
            assertEquals("this is the body.",
                         String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            aWrite(ByteBuffer.wrap("2\r\nth\r\n3\r\nis \r\n8\r\nis the b\r\n4\r\nody.\r\n0\r\n\r\n".toByteArray()), 1000L)
          }
        }
        server.await().close()
        client.await().close()
      }
    }
  }

  @Test
  fun testBodyChunkedInvalidTrailer() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.TRANSFER_ENCODING, "chunked")
            aRead(buffer, 1000L)
            buffer.flip()
            assertEquals(
              Status.BAD_REQUEST,
              Http.body(this, Http.Version.HTTP_1_1, buffer, true, false, headers, null)
            )
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            aWrite(ByteBuffer.wrap("2\r\nth\r\n3\r\nis \r\n8\r\nis the b\r\n4\r\nody.\r\n0\r\n\r\na".toByteArray()), 1000L)
          }
        }
        server.await().close()
        client.await().close()
      }
    }
  }

  @Test
  fun testBodyChunkedSlowConnection() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.TRANSFER_ENCODING, "chunked")
            aRead(buffer, 1000L)
            buffer.flip()
            assertNull(
              Http.body(this, Http.Version.HTTP_1_1, buffer, true, false, headers, null)
            )
            assertEquals("this is the body.",
                         String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            val arr = "2\r\nth\r\n3\r\nis \r\n8\r\nis the b\r\n4\r\nody.\r\n0\r\n\r\n".toByteArray()
            val buf = ByteBuffer.allocateDirect(2)
            for (b in arr) {
              buf.put(b)
              if (buf.position() == 2) {
                buf.flip()
                aWrite(buf, 250L)
                buf.flip()
              }
              delay(100L)
            }
            if (buf.position() > 0) {
              buf.flip()
              aWrite(buf, 250L)
            }
          }
        }
        server.await().close()
        client.await().close()
      }
    }
  }

  @Test
  fun testBodyChunkedWithExtension() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.TRANSFER_ENCODING, "chunked")
            aRead(buffer, 1000L)
            buffer.flip()
            assertNull(
              Http.body(this, Http.Version.HTTP_1_1, buffer, true, false, headers, null)
            )
            assertEquals("this is the body.",
                         String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            aWrite(ByteBuffer.wrap("2; ext=1\r\nth\r\n3; ext=\"a\"\r\nis \r\n8\r\nis the b\r\n4\r\nody.\r\n0\r\n\r\n".toByteArray()), 1000L)
          }
        }
        server.await().close()
        client.await().close()
      }
    }
  }

  @Test
  fun testBodyChunkedWithExtensionSlowConnection() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.TRANSFER_ENCODING, "chunked")
            aRead(buffer, 1000L)
            buffer.flip()
            assertNull(
              Http.body(this, Http.Version.HTTP_1_1, buffer, true, false, headers, null)
            )
            assertEquals("this is the body.",
                         String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            val arr =
              "2; ext=1\r\nth\r\n3; ext=\"a\"\r\nis \r\n8\r\nis the b\r\n4\r\nody.\r\n0\r\n\r\n".toByteArray()
            val buf = ByteBuffer.allocateDirect(2)
            for (b in arr) {
              buf.put(b)
              if (buf.position() == 2) {
                buf.flip()
                aWrite(buf, 250L)
                buf.flip()
              }
              delay(100L)
            }
            if (buf.position() > 0) {
              buf.flip()
              aWrite(buf, 250L)
            }
          }
        }
        server.await().close()
        client.await().close()
      }
    }
  }

  @Test
  fun testBodyChunkedWithTrailer() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.TRANSFER_ENCODING, "chunked")
            aRead(buffer, 1000L)
            buffer.flip()
            assertNull(
              Http.body(this, Http.Version.HTTP_1_1, buffer, true, false, headers, null)
            )
            assertEquals("this is the body.",
                         String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            aWrite(ByteBuffer.wrap("2\r\nth\r\n3\r\nis \r\n8\r\nis the b\r\n4\r\nody.\r\n0\r\nA: a1\r\nB: b1\r\n\r\n".toByteArray()), 1000L)
          }
        }
        server.await().close()
        client.await().close()
      }
    }
  }

  @Test
  fun testBodyChunkedWithTrailerSlowConnection() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.aAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.TRANSFER_ENCODING, "chunked")
            aRead(buffer, 1000L)
            buffer.flip()
            assertNull(
              Http.body(this, Http.Version.HTTP_1_1, buffer, true, false, headers, null)
            )
            assertEquals("this is the body.",
                         String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            aConnect(address)
            val arr =
              "2\r\nth\r\n3\r\nis \r\n8\r\nis the b\r\n4\r\nody.\r\n0\r\nA: a1\r\nB: b1\r\n\r\n".toByteArray()
            val buf = ByteBuffer.allocateDirect(2)
            for (b in arr) {
              buf.put(b)
              if (buf.position() == 2) {
                buf.flip()
                aWrite(buf, 250L)
                buf.flip()
              }
              delay(100L)
            }
            if (buf.position() > 0) {
              buf.flip()
              aWrite(buf, 250L)
            }
          }
        }
        server.await().close()
        client.await().close()
      }
    }
  }

}
