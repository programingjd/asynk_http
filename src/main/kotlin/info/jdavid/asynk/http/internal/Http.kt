@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package info.jdavid.asynk.http.internal

import info.jdavid.asynk.http.Headers
import info.jdavid.asynk.http.Method
import info.jdavid.asynk.http.Status
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

object Http {

  enum class Version {
    HTTP_1_0,
    HTTP_1_1,
    HTTP_2
  }

  fun httpVersion(buffer: ByteBuffer): Version {
    // Status line: (ASCII)
    // HTTP/1.1 CODE MESSAGE\r\n

    // Shortest possible status line is 15 bytes long
    if (buffer.remaining() < 10) throw InvalidStatusLine()

    // It should start with HTTP/1.
    if (buffer.get() != H_UPPER ||
        buffer.get() != T_UPPER ||
        buffer.get() != T_UPPER ||
        buffer.get() != P_UPPER ||
        buffer.get() != SLASH ||
        buffer.get() != ONE ||
        buffer.get() != DOT) throw InvalidStatusLine()
    return when (buffer.get()) {
      ONE -> Version.HTTP_1_1
      ZERO -> Version.HTTP_1_0
      else -> throw InvalidStatusLine()
    }
  }

  fun status(buffer: ByteBuffer): Int {
    // Status line: (ASCII)
    // HTTP/1.1 CODE MESSAGE\r\n
    // We've already read up to the HTTP version (HTTP/1.0 or HTTP/1.1)

    if (buffer.get() != SPACE) throw InvalidStatusLine()

    val codeBytes = ByteArray(3)
    buffer.get(codeBytes)
    val code = String(codeBytes).toInt()

    while (true) {
      if (buffer.remaining() < 1) throw InvalidStatusLine()
      if (buffer.get() == CR) break
    }
    if (buffer.get() != LF) throw InvalidStatusLine()
    return code
  }

  fun method(buffer: ByteBuffer): Method? {
    // Request line: (ASCII)
    // METHOD URI HTTP/1.1\r\n
    // Look for first space -> METHOD

    // Shortest possible request line is 16 bytes long
    if (buffer.remaining() < 16) return null

    // 1. Look for first space to extract METHOD
    var i = 0
    while (true) {
      if (i == 10) return null
      val b = buffer[i++]
      if (validMethod(b)) continue
      if (b == SPACE) break
      return null
    }
    val methodBytes = ByteArray(i - 1)
    buffer.get(methodBytes)
    buffer.get()
    return Method.from(String(methodBytes, Charsets.US_ASCII))
  }

  fun uri(buffer: ByteBuffer): String? {
    // Request line: (ASCII)
    // METHOD URI HTTP/1.1\r\n
    // 1. look for first space -> METHOD (already done)
    // 2. look for second space -> URI (should start with a slash)
    // 3. check that rest of line is correct.
    //
    // URI can be:
    // * (usually used with OPTIONS to allow server-wide CORS) -> not supported.
    // an URL (for calls to a proxy)
    // an absolute path

    var i = buffer.position()
    val j = i
    while (true) {
      if (i == buffer.remaining()) return null
      val b = buffer[i++]
      if (validUrl(b)) continue
      if (b == SPACE) break
      return null
    }
    val uriBytes = ByteArray(i - j - 1)
    buffer.get(uriBytes)
    val uri = String(uriBytes)
    buffer.get()

    // 3. HTTP/1.1\r\n should follow
    try {
      httpVersion(buffer)
    }
    catch (e: InvalidStatusLine) {
      return null
    }

    if (buffer.get() != CR || buffer.get() != LF) return null

    if (uri[0] == '/') return uri // absolute path

    // url
    if (uri[0] != 'h' || uri[1] != 't' || uri[2] != 't' || uri[3] != 'p') return null
    if (uri[4] == ':') {
      if (uri[5] != '/' || uri[6] != '/') return null
      val n = uri.length
      val q = uri.indexOf('#', 7).let { if (it == -1) n else it }
      val h = uri.indexOf('?', 7).let { if (it == -1) n else it }
      val s = uri.indexOf('/', 7)
      return if (s == -1) "/" + uri.substring(Math.min(q, h)) else uri.substring(s)
    }
    else if (uri[4] != 's' || uri[5] != ':') return null
    if (uri[6] != '/' || uri[7] != '/') return null
    val n = uri.length
    val q = uri.indexOf('#', 8).let { if (it == -1) n else it }
    val h = uri.indexOf('?', 8).let { if (it == -1) n else it }
    val s = uri.indexOf('/', 8)
    return if (s == -1) "/" + uri.substring(Math.min(q, h)) else uri.substring(s)
  }

  suspend fun headers(socket: AsynchronousSocketChannel,
                      socketAccess: SocketAccess,
                      buffer: ByteBuffer,
                      headers: Headers,
                      timeout: Long = 5000L,
                      maxSize: Int = 8192): Boolean {
    // Headers
    // FIELD_NAME_1: FIELD_VALUE_1\r\n
    // ...
    // FIELD_NAME_N: FIELD_VALUE_N\r\n
    // \r\n
    // Add content between \r\n as header lines until an empty line signifying the end of the headers.

    var i = buffer.position()
    var size = 0
    var j = i
    while (true) {
      if (++size > maxSize) throw HeadersTooLarge()
      if (when (buffer[i++]) {
        LF -> {
          if (buffer[i - 2] != CR) return false
          if (i - 2 == j){
            buffer.get()
            buffer.get()
            true
          }
          else {
            val headerBytes = ByteArray(i - j - 2)
            buffer.get(headerBytes)
            headers.lines.add(String(headerBytes, Charsets.ISO_8859_1))
            buffer.get()
            buffer.get()
            j = i
            false
          }
        }
        else -> false
      }) break
      if (i == buffer.limit()) {
        buffer.compact()
        if (buffer.position() == buffer.capacity()) throw HeadersTooLarge()
        if (timeout > 0L) {
          if (withTimeout(timeout) { socketAccess.asyncRead(socket, buffer) } < 1L) return false
        }
        else if (socketAccess.asyncRead(socket, buffer) < 1L) return false
        buffer.flip()
        i -= j
        j = 0
      }
    }
    return true
  }

  suspend fun body(socket2: AsynchronousSocketChannel,
                   socketAccess: SocketAccess,
                   version: Version,
                   buffer: ByteBuffer,
                   context: Context,
                   bodyAllowed: Boolean,
                   bodyRequired: Boolean,
                   headers: Headers,
                   continueBuffer: ByteBuffer?,
                   timeout: Long = 5000L): Int {
    buffer.compact().flip()
    var buf = buffer
    val encoding = headers.value(Headers.TRANSFER_ENCODING)
    if (encoding == null || encoding == IDENTITY) {
      val contentLength =
        headers.value(Headers.CONTENT_LENGTH)?.toInt() ?:
        if (version == Version.HTTP_1_0 || headers.value(Headers.CONTENT_TYPE) != null) {
          buffer.position(buffer.limit()).limit(buffer.capacity())
          while (true) {
            if (buf.position() == buf.capacity()) {
              if (buf == buffer && context.maxRequestSize > buffer.capacity()) {
                val position = buffer.position()
                buffer.position(0)
                buf = ByteBuffer.allocateDirect(context.maxRequestSize) ?: return Status.PAYLOAD_TOO_LARGE
                buf.put(buffer)
                buf.position(position)
              }
              else return Status.PAYLOAD_TOO_LARGE
            }
            if (timeout > 0L) {
              if (withTimeout(timeout) { socketAccess.asyncRead(socket2, buf) } < 1L) break
            }
            else if (socketAccess.asyncRead(socket2, buf) < 1L) break
          }
          buf.limit(buf.position()).position(0)
          buf.limit()
        } else 0
      if (buf.limit() > contentLength) return Status.BAD_REQUEST
      if (contentLength > 0) {
        if (!bodyAllowed) return Status.BAD_REQUEST
        val compression = headers.value(Headers.CONTENT_ENCODING)
        if (compression != null && compression != IDENTITY) return Status.UNSUPPORTED_MEDIA_TYPE
        if (contentLength > buf.capacity()) {
          if (buf == buffer && context.maxRequestSize > buf.capacity()) {
            val limit = buffer.limit()
            val position = buffer.position()
            buffer.position(0)
            buf = ByteBuffer.allocateDirect(contentLength) ?: return Status.PAYLOAD_TOO_LARGE
            buf.put(buffer)
            buf.position(position).limit(limit)
          }
          else  return Status.PAYLOAD_TOO_LARGE
        }
        if (continueBuffer != null && headers.value(Headers.EXPECT)?.toLowerCase() == ONE_HUNDRED_CONTINUE) {
          if (buf.remaining() > 0) return Status.UNSUPPORTED_MEDIA_TYPE
          continueBuffer.rewind()
          while (continueBuffer.remaining() > 0) {
            if (timeout > 0L) {
              withTimeout(timeout) { socketAccess.asyncWrite(socket2, continueBuffer) }
            }
            else socketAccess.asyncWrite(socket2, continueBuffer)
          }
        }
        while (contentLength > buf.limit()) {
          val limit = buf.limit()
          buf.position(limit).limit(buf.capacity())
          if (timeout > 0L) {
            if (withTimeout(timeout) { socketAccess.asyncRead(socket2, buf) } < 1L) return Status.BAD_REQUEST
          }
          else if (socketAccess.asyncRead(socket2, buf) < 1L) return Status.BAD_REQUEST
          buf.limit(buf.position()).position(0)
          if (buf.limit() > contentLength) return Status.BAD_REQUEST
        }
      }
    }
    else if (encoding == CHUNKED) {
      if (!bodyAllowed) return Status.BAD_REQUEST
      if (headers.value(Headers.TRAILER) != null) return Status.BAD_REQUEST
      if (continueBuffer != null && headers.value(Headers.EXPECT)?.toLowerCase() == ONE_HUNDRED_CONTINUE) {
        if (buffer.remaining() > 0) return Status.UNSUPPORTED_MEDIA_TYPE
        continueBuffer.rewind()
        while (continueBuffer.remaining() > 0) {
          if (timeout > 0L) {
            withTimeout(timeout) { socketAccess.asyncWrite(socket2, continueBuffer) }
          }
          else socketAccess.asyncWrite(socket2, continueBuffer)
        }
      }
      // Body with chunked encoding
      // CHUNK_1_LENGTH_HEX\r\n
      // CHUNK_1_BYTES\r\n
      // ...
      // CHUNK_N_LENGTH_HEX\r\n
      // CHUNK_N_BYTES\r\n
      // 0\r\n
      // FIELD_NAME_1: FIELD_VALUE_1\r\n
      // ...
      // FIELD_NAME_N: FIELD_VALUE_N\r\n
      // \r\n
      // Trailing header fields are ignored.
      val sb = StringBuilder(12)
      var start = buffer.position()
      chunks@ while (true) { // for each chunk
        // Look for \r\n to extract the chunk length
        bytes@ while (true) {
          if (buf.remaining() == 0) {
            val limit = buf.limit()
            if (buf.capacity() == limit) {
              if (buf == buffer && context.maxRequestSize > buffer.capacity()) {
                val position = buffer.position()
                buffer.position(0)
                buf = ByteBuffer.allocateDirect(context.maxRequestSize) ?: return Status.PAYLOAD_TOO_LARGE
                buf.put(buffer)
                buf.position(position).limit(limit)
              }
              else return Status.PAYLOAD_TOO_LARGE
            }
            val position = buf.position()
            buf.position(limit).limit(buf.capacity())
            if (timeout > 0L) {
              if (withTimeout(timeout) { socketAccess.asyncRead(socket2, buf) } < 1L) return Status.BAD_REQUEST
            }
            else if (socketAccess.asyncRead(socket2, buf) < 1L) return Status.BAD_REQUEST
            buf.limit(buf.position()).position(position)
          }
          val b = buf.get()
          if (b == LF) { // End of chunk size line
            if (sb.last().toByte() != CR) return Status.BAD_REQUEST
            val index = sb.indexOf(';') // ignore chunk extensions
            val chunkSize = Integer.parseInt(
              if (index == -1) sb.trim().toString() else sb.substring(0, index).trim(),
              16
            )
            // remove chunk size line bytes from the buffer, and skip the chunk bytes
            sb.delete(0, sb.length)
            val end = buf.position()
            val limit = buf.limit()
            buf.position(start)
            (buf.slice().position(end - start) as ByteBuffer).compact()
            buf.limit(limit - end + start)
            if (buf.capacity() - start < chunkSize + 2) {
              if (buf == buffer && context.maxRequestSize > buffer.capacity()) {
                buffer.position(0)
                buf = ByteBuffer.allocateDirect(context.maxRequestSize) ?: return Status.PAYLOAD_TOO_LARGE
                buf.put(buffer)
                buf.position(start).limit(limit - end + start)
              }
              else return Status.PAYLOAD_TOO_LARGE
            }
            while (buf.limit() < start + chunkSize + 2) {
              buf.position(buf.limit()).limit(buf.capacity())
              if (timeout > 0L) {
                if (withTimeout(timeout) { socketAccess.asyncRead(socket2, buf) } < 1L) return Status.BAD_REQUEST
              }
              else if (socketAccess.asyncRead(socket2, buf) < 1L) return Status.BAD_REQUEST
              buf.limit(buf.position())
            }
            buf.position(start + chunkSize)
            if (chunkSize == 0) {
              // Trailing headers are not supported because unless you specify "TE: trailer" in the request
              // headers, then the server should not set any trailing header.
              if (buf.get() != CR || buf.get() != LF) return Status.BAD_REQUEST
              if (buf.remaining() > 0) return Status.BAD_REQUEST
              buf.position(0).limit(start)
              break@chunks
            }
            else {
              if (buf.get() != CR || buf.get() != LF) return Status.BAD_REQUEST
              start = buf.position() - 2
              break@bytes
            }
          }
          sb.append(b.toChar())
        }
      }
    }
    if (!bodyAllowed && buf.remaining() > 0) return Status.BAD_REQUEST
    if (bodyRequired && buf.remaining() == 0) return Status.BAD_REQUEST
    if (buf == buffer) return 0
    context.buffer = buf
    return 1
  }

  private const val CR: Byte = 0x0d
  private const val LF: Byte = 0x0a
  private const val SPACE: Byte = 0x20
  private const val H_UPPER: Byte = 0x48
  private const val T_UPPER: Byte = 0x54
  private const val P_UPPER: Byte = 0x50
  private const val SLASH: Byte = 0x2f
  private const val ONE: Byte = 0x31
  private const val DOT: Byte = 0x2e
  private const val ZERO: Byte = 0x30
  private const val UNDERSCORE: Byte = 0x5f
  private const val EQUALS: Byte = 0x3d
  private const val EXCLAMATION_POINT: Byte = 0x21
  private const val AT: Byte = 0x40
  private const val LEFT_SQUARE_BRACKET: Byte = 0x5b
  private const val RIGHT_SQUARE_BRACKET: Byte = 0x5b
  private const val DOUBLE_QUOTE: Byte = 0x22
  private const val LOWER_THAN: Byte = 0x3C
  private const val GREATER_THAN: Byte = 0x3C
  private const val BACKSLASH: Byte = 0x5c
  private const val BACKTICK: Byte = 0x60
  private const val LEFT_CURLY_BRACE: Byte = 0x7b
  private const val TILDA: Byte = 0x7e

  private const val ONE_HUNDRED_CONTINUE = "100-continue"
  private const val IDENTITY = "identity"
  private const val CHUNKED = "chunked"

  private fun validMethod(b: Byte): Boolean {
    @Suppress("ConvertTwoComparisonsToRangeCheck")
    return b > AT && b < LEFT_SQUARE_BRACKET
  }

  private fun validUrl(b: Byte): Boolean {
    @Suppress("ConvertTwoComparisonsToRangeCheck")
    return b == EXCLAMATION_POINT || (b > DOUBLE_QUOTE && b < LOWER_THAN) || b == EQUALS ||
           (b > GREATER_THAN && b < BACKSLASH) || b == RIGHT_SQUARE_BRACKET || b == UNDERSCORE ||
           (b > BACKTICK && b < LEFT_CURLY_BRACE) || b == TILDA
  }

  class InvalidStatusLine: Exception()
  class InvalidHeaders(): Exception()
  class HeadersTooLarge : Exception()
  class InvalidResponse: Exception()
  class BodyTooLarge : Exception()
  class UnsupportedContentEncoding: Exception()

}
