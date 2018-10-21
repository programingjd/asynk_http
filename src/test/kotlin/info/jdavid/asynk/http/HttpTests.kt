package info.jdavid.asynk.http

import info.jdavid.asynk.core.asyncAccept
import info.jdavid.asynk.core.asyncConnect
import info.jdavid.asynk.core.asyncRead
import info.jdavid.asynk.core.asyncWrite
import info.jdavid.asynk.http.internal.Context
import info.jdavid.asynk.http.internal.Http
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.security.SecureRandom

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
    ByteBuffer.wrap(
      "GET http://example.com HTTP/1.1\r\n".toByteArray()
    ).apply {
      assertEquals(Method.GET, Http.method(this))
      assertEquals("/", Http.uri(this))
    }
    ByteBuffer.wrap(
      "GET https://example.com/abc HTTP/1.1\r\n".toByteArray()
    ).apply {
      assertEquals(Method.GET, Http.method(this))
      assertEquals("/abc", Http.uri(this))
    }
    ByteBuffer.wrap(
      "GET https://example.com?a=b HTTP/1.1\r\n".toByteArray()
    ).apply {
      assertEquals(Method.GET, Http.method(this))
      assertEquals("/?a=b", Http.uri(this))
    }
    ByteBuffer.wrap(
      "GET https://example.com#frag HTTP/1.1\r\n".toByteArray()
    ).apply {
      assertEquals(Method.GET, Http.method(this))
      assertEquals("/#frag", Http.uri(this))
    }
    ByteBuffer.wrap(
      "GET https://example.com/abc/def#frag HTTP/1.1\r\n".toByteArray()
    ).apply {
      assertEquals(Method.GET, Http.method(this))
      assertEquals("/abc/def#frag", Http.uri(this))
    }
    ByteBuffer.wrap(
      "### / HTTP/1.1\r\n".toByteArray()
    ).apply {
      assertNull(Http.method(this))
    }
    ByteBuffer.wrap(
      "GET / HTTP/1.0\r\n".toByteArray()
    ).apply {
      assertEquals(Method.GET, Http.method(this))
      assertNull(Http.uri(this))
    }
    ByteBuffer.wrap(
      "GET /absolute/path\r\n".toByteArray()
    ).apply {
      assertEquals(Method.GET, Http.method(this))
      assertNull(Http.uri(this))
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
    ByteBuffer.wrap("HTTP/2 400 BAD REQUEST\r\n".toByteArray()).apply {
      try {
        Http.httpVersion(this)
        fail<Nothing>()
      }
      catch (ignore: Http.InvalidStatusLine) {}
    }
    ByteBuffer.wrap("HTTP/1. 500 INTERNAL SERVER ERROR\r\n".toByteArray()).apply {
      try {
        Http.httpVersion(this)
        fail<Nothing>()
      }
      catch (ignore: Http.InvalidStatusLine) {}
    }
    ByteBuffer.wrap("200 OK\r\n".toByteArray()).apply {
      try {
        Http.httpVersion(this)
        fail<Nothing>()
      }
      catch (ignore: Http.InvalidStatusLine) {}
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
          it.asyncAccept().apply {
            val headers = Headers()
            withTimeout(1000L) { asyncRead(buffer) }
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
            asyncConnect(address)
            withTimeout(1000) {
              asyncWrite(ByteBuffer.wrap("A:a1\r\nB: b1\r\nC: c1; c2\r\nA: a2\r\n\r\n".toByteArray()))
            }
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
          it.asyncAccept().apply {
            val headers = Headers()
            withTimeout(1000L) { asyncRead(buffer) }
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
            asyncConnect(address)
            val arr = "A:a1\r\nB: b1\r\nC: c1; c2\r\nA: a2\r\n\r\n".toByteArray()
            val buf = ByteBuffer.allocateDirect(2)
            for (b in arr) {
              buf.put(b)
              if (buf.position() == 2) {
                buf.flip()
                withTimeout(250L) { asyncWrite(buf) }
                buf.flip()
              }
              delay(100L)
            }
            if (buf.position() > 0) {
              buf.flip()
              withTimeout(250L) { asyncWrite(buf) }
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
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 16384
    }
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().add(Headers.CONTENT_TYPE, MediaType.TEXT)
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(
              Status.BAD_REQUEST,
              Http.body(
                this,
                Http.Version.HTTP_1_0,
                buffer,
                context,
                false,
                false,
                headers,
                null
              )
            )
            assertNull(context.buffer)
          }
        }
        launch {
          AsynchronousSocketChannel.open().apply {
            asyncConnect(address)
            withTimeout(1000L) { asyncWrite(ByteBuffer.wrap("this is the body.".toByteArray())) }
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
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 16384
    }
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.CONTENT_LENGTH,"17")
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(
              Status.BAD_REQUEST,
              Http.body(
                this,
                Http.Version.HTTP_1_1,
                buffer,
                context,
                false,
                false,
                headers,
                null
              )
            )
            assertNull(context.buffer)
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            asyncConnect(address)
            withTimeout(1000L) { asyncWrite(ByteBuffer.wrap("this is the body.".toByteArray())) }
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
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 16384
    }
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().add(Headers.CONTENT_TYPE, MediaType.TEXT)
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(0, Http.body(
              this,
              Http.Version.HTTP_1_0,
              buffer,
              context,
              true,
              false,
              headers,
              null))
            assertEquals("this is the body.",
                         String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
            assertNull(context.buffer)
          }
        }
        launch {
          AsynchronousSocketChannel.open().apply {
            asyncConnect(address)
            withTimeout(1000L) { asyncWrite(ByteBuffer.wrap("this is the body.".toByteArray())) }
            close()
          }
        }
        server.await().close()
      }
    }
  }

  @Test
  fun testLargeBodyHttp10() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 16384
    }
    val bytes = SecureRandom.getSeed(12000)
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().add(Headers.CONTENT_TYPE, MediaType.TEXT)
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(
              1,
              Http.body(
                this,
                Http.Version.HTTP_1_0,
                buffer,
                context,
                true,
                false,
                headers,
                null
              )
            )
            val buf = context.buffer ?: fail<Nothing>("Context buffer should not be null.")
            assertEquals(Crypto.hex(bytes),
                         Crypto.hex(ByteArray(buf.remaining()).apply { buf.get(this) }))
          }
        }
        launch {
          AsynchronousSocketChannel.open().apply {
            asyncConnect(address)
            withTimeout(1000L) { asyncWrite(ByteBuffer.wrap(bytes)) }
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
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 16384
    }
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().add(Headers.CONTENT_TYPE, MediaType.TEXT)
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(
              0,
              Http.body(
                this,
                Http.Version.HTTP_1_0,
                buffer,
                context,
                true,
                false,
                headers,
                null
              )
            )
            assertNull(context.buffer)
            assertEquals("this is the body.",
                         String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
          }
        }
        launch {
          AsynchronousSocketChannel.open().apply {
            asyncConnect(address)
            val arr = "this is the body.".toByteArray()
            val buf = ByteBuffer.allocateDirect(2)
            for (b in arr) {
              buf.put(b)
              if (buf.position() == 2) {
                buf.flip()
                withTimeout(250L) { asyncWrite(buf) }
                buf.flip()
              }
              delay(100L)
            }
            if (buf.position() > 0) {
              buf.flip()
              withTimeout(250L) { asyncWrite(buf) }
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
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 16384
    }
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.CONTENT_LENGTH, "17")
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(
              0,
              Http.body(
                this,
                Http.Version.HTTP_1_1,
                buffer,
                context,
                true,
                false,
                headers,
                null)
            )
            assertNull(context.buffer)
            assertEquals("this is the body.",
                         String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            asyncConnect(address)
            withTimeout(1000L) { asyncWrite(ByteBuffer.wrap("this is the body.".toByteArray())) }
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
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 16384
    }
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.CONTENT_LENGTH, "17")
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(
              0,
              Http.body(
                this,
                Http.Version.HTTP_1_1,
                buffer,
                context,
                true,
                false,
                headers,
                null
              )
            )
            assertNull(context.buffer)
            assertEquals("this is the body.",
                         String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            asyncConnect(address)
            val arr = "this is the body.".toByteArray()
            val buf = ByteBuffer.allocateDirect(2)
            for (b in arr) {
              buf.put(b)
              if (buf.position() == 2) {
                buf.flip()
                withTimeout(250L) { asyncWrite(buf) }
                buf.flip()
              }
              delay(100L)
            }
            if (buf.position() > 0) {
              buf.flip()
              withTimeout(250L) { asyncWrite(buf) }
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
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 16384
    }
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.CONTENT_LENGTH, "16")
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(
              Status.BAD_REQUEST,
              Http.body(
                this,
                Http.Version.HTTP_1_1,
                buffer,
                context,
                true,
                false,
                headers,
                null
              )
            )
            assertNull(context.buffer)
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            asyncConnect(address)
            withTimeout(1000L) { asyncWrite(ByteBuffer.wrap("this is the body.".toByteArray())) }
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
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 48
    }
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT)
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(
              Status.PAYLOAD_TOO_LARGE,
              Http.body(
                this,
                Http.Version.HTTP_1_0,
                buffer,
                context,
                true,
                false,
                headers,
                null
              )
            )
            assertNull(context.buffer)
          }
        }
        launch {
          AsynchronousSocketChannel.open().apply {
            asyncConnect(address)
            withTimeout(1000L) {
              asyncWrite(
                ByteBuffer.wrap("this is a body that is too large to fit in the buffer.".toByteArray())
              )
            }
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
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 48
    }
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.CONTENT_LENGTH, "54")
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(
              Status.PAYLOAD_TOO_LARGE,
              Http.body(
                this,
                Http.Version.HTTP_1_1,
                buffer,
                context,
                true,
                false,
                headers,
                null
              )
            )
            assertNull(context.buffer)
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            asyncConnect(address)
            withTimeout(1000L) {
              asyncWrite(
                ByteBuffer.wrap("this is a body that is too large to fit in the buffer.".toByteArray())
              )
            }
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
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 16384
    }
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.CONTENT_ENCODING, "br")
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(
              Status.UNSUPPORTED_MEDIA_TYPE,
              Http.body(
                this,
                Http.Version.HTTP_1_0,
                buffer,
                context,
                true,
                false,
                headers,
                null
              )
            )
            assertNull(context.buffer)
          }
        }
        launch {
          AsynchronousSocketChannel.open().apply {
            asyncConnect(address)
            withTimeout(1000L) { asyncWrite(ByteBuffer.wrap("this is the body.".toByteArray())) }
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
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 16384
    }
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.CONTENT_LENGTH, "17").
              add(Headers.CONTENT_ENCODING, "br")
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(
              Status.UNSUPPORTED_MEDIA_TYPE,
              Http.body(
                this,
                Http.Version.HTTP_1_1,
                buffer,
                context,
                true,
                false,
                headers,
                null
              )
            )
            assertNull(context.buffer)
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            asyncConnect(address)
            withTimeout(1000L) { asyncWrite(ByteBuffer.wrap("this is the body.".toByteArray())) }
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
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 16384
    }
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.TRANSFER_ENCODING, "chunked")
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(
              0,
              Http.body(
                this,
                Http.Version.HTTP_1_1,
                buffer,
                context,
                true,
                false,
                headers,
                null
              )
            )
            assertNull(context.buffer)
            assertEquals("this is the body.",
                         String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            asyncConnect(address)
            withTimeout(1000L) {
              asyncWrite(ByteBuffer.wrap(
                "2\r\nth\r\n3\r\nis \r\n8\r\nis the b\r\n4\r\nody.\r\n0\r\n\r\n".toByteArray()
              ))
            }
          }
        }
        server.await().close()
        client.await().close()
      }
    }
  }

  @Test
  fun testLargeBodyChunked() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    val bytes1 = SecureRandom.getSeed(2000)
    val bytes2 = SecureRandom.getSeed(3000)
    val bytes3 = SecureRandom.getSeed(100)
    val bytes4 = SecureRandom.getSeed(7000)
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 16384
    }
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.TRANSFER_ENCODING, "chunked")
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(
              1,
              Http.body(
                this,
                Http.Version.HTTP_1_1,
                buffer,
                context,
                true,
                false,
                headers,
                null
              )
            )
            val buf = context.buffer ?: fail<Nothing>("Context buffer should not be null.")
            assertEquals(Crypto.hex(bytes1) + Crypto.hex(bytes2) + Crypto.hex(bytes3) + Crypto.hex(bytes4),
                         Crypto.hex(ByteArray(buf.remaining()).apply { buf.get(this) }))
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            val chunked = ByteBuffer.allocate(20000)
            chunked.put("${bytes1.size.toString(16)}\r\n".toByteArray())
            chunked.put(bytes1)
            chunked.put("\r\n".toByteArray())
            chunked.put("${bytes2.size.toString(16)}\r\n".toByteArray())
            chunked.put(bytes2)
            chunked.put("\r\n".toByteArray())
            chunked.put("${bytes3.size.toString(16)}\r\n".toByteArray())
            chunked.put(bytes3)
            chunked.put("\r\n".toByteArray())
            chunked.put("${bytes4.size.toString(16)}\r\n".toByteArray())
            chunked.put(bytes4)
            chunked.put("\r\n".toByteArray())
            chunked.put("0\r\n\r\n".toByteArray())
            chunked.flip()
            asyncConnect(address)
            withTimeout(1000L) {
              asyncWrite(chunked)
            }
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
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 16384
    }
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.TRANSFER_ENCODING, "chunked")
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(
              0,
              Http.body(
                this,
                Http.Version.HTTP_1_1,
                buffer,
                context,
                true,
                false,
                headers,
                null
              )
            )
            assertNull(context.buffer)
            assertEquals("this is the body.",
                         String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            asyncConnect(address)
            val arr = "2\r\nth\r\n3\r\nis \r\n8\r\nis the b\r\n4\r\nody.\r\n0\r\n\r\n".toByteArray()
            val buf = ByteBuffer.allocateDirect(2)
            for (b in arr) {
              buf.put(b)
              if (buf.position() == 2) {
                buf.flip()
                withTimeout(250L) { asyncWrite(buf) }
                buf.flip()
              }
              delay(100L)
            }
            if (buf.position() > 0) {
              buf.flip()
              withTimeout(250L) { asyncWrite(buf) }
            }
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
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 16384
    }
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.TRANSFER_ENCODING, "chunked")
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(
              Status.BAD_REQUEST,
              Http.body(
                this,
                Http.Version.HTTP_1_1,
                buffer,
                context,
                true,
                false,
                headers,
                null
              )
            )
            assertNull(context.buffer)
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            asyncConnect(address)
            withTimeout(1000L) {
              asyncWrite(ByteBuffer.wrap(
                "2\r\nth\r\n3\r\nis \r\n8\r\nis the b\r\n4\r\nody.\r\n0\r\n\r\naaa".toByteArray()
              ))
            }
          }
        }
        server.await().close()
        client.await().close()
      }
    }
  }

  @Test
  fun testBodyChunkedInvalidTrailerSlowConnection() {
    val buffer = ByteBuffer.allocateDirect(4096)
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 16384
    }
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.TRANSFER_ENCODING, "chunked")
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(
              Status.BAD_REQUEST,
              Http.body(
                this,
                Http.Version.HTTP_1_1,
                buffer,
                context,
                true,
                false,
                headers,
                null
              )
            )
            assertNull(context.buffer)
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            asyncConnect(address)
            val arr = "2\r\nth\r\n3\r\nis \r\n8\r\nis the b\r\n5\r\nody. \r\n0\r\n\r\na".toByteArray()
            val buf = ByteBuffer.allocateDirect(2)
            for (b in arr) {
              buf.put(b)
              if (buf.position() == 2) {
                buf.flip()
                withTimeout(250L) { asyncWrite(buf) }
                buf.flip()
              }
              delay(100L)
            }
            if (buf.position() > 0) {
              buf.flip()
              withTimeout(250L) { asyncWrite(buf) }
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
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 16384
    }
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.TRANSFER_ENCODING, "chunked")
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(
              0,
              Http.body(
                this,
                Http.Version.HTTP_1_1,
                buffer,
                context,
                true,
                false,
                headers,
                null
              )
            )
            assertNull(context.buffer)
            assertEquals("this is the body.",
                         String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            asyncConnect(address)
            withTimeout(1000L) {
              asyncWrite(ByteBuffer.wrap(
                "2; ext=1\r\nth\r\n3; ext=\"a\"\r\nis \r\n8\r\nis the b\r\n4\r\nody.\r\n0\r\n\r\n".toByteArray()
              ))
            }
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
    val context = object: Context {
      override var buffer: ByteBuffer? = null
      override val maxRequestSize: Int = 16384
    }
    runBlocking {
      AsynchronousServerSocketChannel.open().use {
        it.bind(address)
        val server = async {
          it.asyncAccept().apply {
            val headers = Headers().
              add(Headers.CONTENT_TYPE, MediaType.TEXT).
              add(Headers.TRANSFER_ENCODING, "chunked")
            withTimeout(1000L) { asyncRead(buffer) }
            buffer.flip()
            assertEquals(
              0,
              Http.body(
                this,
                Http.Version.HTTP_1_1,
                buffer,
                context,
                true,
                false,
                headers,
                null
              )
            )
            assertNull(context.buffer)
            assertEquals("this is the body.",
                         String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
          }
        }
        val client = async {
          AsynchronousSocketChannel.open().apply {
            asyncConnect(address)
            val arr =
              "2; ext=1\r\nth\r\n3; ext=\"a\"\r\nis \r\n8\r\nis the b\r\n4\r\nody.\r\n0\r\n\r\n".toByteArray()
            val buf = ByteBuffer.allocateDirect(2)
            for (b in arr) {
              buf.put(b)
              if (buf.position() == 2) {
                buf.flip()
                withTimeout(250L) { asyncWrite(buf) }
                buf.flip()
              }
              delay(100L)
            }
            if (buf.position() > 0) {
              buf.flip()
              withTimeout(250L) { asyncWrite(buf) }
            }
          }
        }
        server.await().close()
        client.await().close()
      }
    }
  }

}
