@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package info.jdavid.asynk.http.internal

import info.jdavid.asynk.http.Headers
import info.jdavid.asynk.http.Method
import info.jdavid.asynk.http.Status
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit

object Http {

  fun httpVersion(buffer: ByteBuffer): Boolean {
    // Status line: (ASCII)
    // HTTP/1.1 CODE MESSAGE\r\n

    // Shortest possible status line is 15 bytes long
    if (buffer.remaining() < 15) throw InvalidStatusLine()

    // It should start with HTTP/1.
    if (buffer.get() != H_UPPER ||
        buffer.get() != T_UPPER ||
        buffer.get() != T_UPPER ||
        buffer.get() != P_UPPER ||
        buffer.get() != SLASH ||
        buffer.get() != ONE ||
        buffer.get() != DOT) throw InvalidStatusLine()
    return when (buffer.get()) {
      ONE -> false
      ZERO -> true
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
      if (i == 7) return null
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
    if (buffer.get() != H_UPPER ||
        buffer.get() != T_UPPER ||
        buffer.get() != T_UPPER ||
        buffer.get() != P_UPPER ||
        buffer.get() != SLASH ||
        buffer.get() != ONE ||
        buffer.get() != DOT ||
        buffer.get() != ONE ||
        buffer.get() != CR ||
        buffer.get() != LF) return null

    if (uri[0] == '/') return uri // absolute path

    // url
    if (uri[0] != 'h' || uri[1] != 't' || uri[2] != 't' || uri[3] != 'p') return null
    if (uri[5] == ':') {
      if (uri[6] != '/' || uri[7] != '/') return null
      return uri.substring(7)
    }
    else if (uri[5] != 's' || uri[6] != ':') return null
    if (uri[7] != '/' || uri[7] != '/') return null
    return uri.substring(8)
  }

  suspend fun headers(socket: AsynchronousSocketChannel,
                      buffer: ByteBuffer,
                      headers: Headers,
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
        if (socket.aRead(buffer, 3000L, TimeUnit.MILLISECONDS) < 1) return false
        buffer.flip()
        i -= j
        j = 0
      }
    }
    return true
  }

  suspend fun body(socket: AsynchronousSocketChannel,
                   buffer: ByteBuffer,
                   bodyAllowed: Boolean,
                   bodyRequired: Boolean,
                   headers: Headers,
                   continueBuffer: ByteBuffer?): Int? {
    buffer.compact().flip()
    val encoding = headers.value(Headers.TRANSFER_ENCODING)
    if (encoding == null || encoding == IDENTITY) {
      val contentLength = headers.value(Headers.CONTENT_LENGTH)?.toInt() ?: 0
      if (buffer.limit() > contentLength) return Status.BAD_REQUEST
      if (contentLength > 0) {
        if (!bodyAllowed) return Status.BAD_REQUEST
        val compression = headers.value(Headers.CONTENT_ENCODING)
        if (compression != null && compression != IDENTITY) return Status.UNSUPPORTED_MEDIA_TYPE
        if (contentLength > buffer.capacity()) return Status.PAYLOAD_TOO_LARGE
        if (continueBuffer != null && headers.value(Headers.EXPECT)?.toLowerCase() == ONE_HUNDRED_CONTINUE) {
          if (buffer.remaining() > 0) return Status.UNSUPPORTED_MEDIA_TYPE
          continueBuffer.rewind()
          while (continueBuffer.remaining() > 0) socket.aWrite(continueBuffer)
        }
        while (contentLength > buffer.limit()) {
          val limit = buffer.limit()
          buffer.position(limit).limit(buffer.capacity())
          if (socket.aRead(buffer, 5000L, TimeUnit.MILLISECONDS) < 1) return Status.BAD_REQUEST
          buffer.limit(buffer.position()).position(limit)
          if (buffer.limit() > contentLength) return Status.BAD_REQUEST
        }
      }
    }
    else if (encoding == CHUNKED) {
      if (!bodyAllowed) return Status.BAD_REQUEST
      if (continueBuffer != null && headers.value(Headers.EXPECT)?.toLowerCase() == ONE_HUNDRED_CONTINUE) {
        if (buffer.remaining() > 0) return Status.UNSUPPORTED_MEDIA_TYPE
        continueBuffer.rewind()
        while (continueBuffer.remaining() > 0) socket.aWrite(continueBuffer)
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
          if (buffer.remaining() == 0) {
            val limit = buffer.limit()
            if (buffer.capacity() == limit) return Status.PAYLOAD_TOO_LARGE
            buffer.position(limit).limit(buffer.capacity())
            if (socket.aRead(buffer, 3000L, TimeUnit.MILLISECONDS) < 1) return Status.BAD_REQUEST
            buffer.limit(buffer.position()).position(limit)
          }
          val b = buffer.get()
          if (b == LF) { // End of chunk size line
            if (sb.last().toByte() != CR) return Status.BAD_REQUEST
            val index = sb.indexOf(';') // ignore chunk extensions
            val chunkSize = Integer.parseInt(
              if (index == -1) sb.trim().toString() else sb.substring(0, index).trim(),
              16
            )
            // remove chunk size line bytes from the buffer, and skip the chunk bytes
            sb.delete(0, sb.length)
            val end = buffer.position()
            val limit = buffer.limit()
            buffer.position(start)
            (buffer.slice().position(end - start) as ByteBuffer).compact()
            buffer.limit(limit - end + start)
            if (buffer.capacity() - start < chunkSize + 2) return Status.PAYLOAD_TOO_LARGE
            while (buffer.limit() < start + chunkSize + 2) {
              buffer.position(buffer.limit())
              if (socket.aRead(buffer, 3000L, TimeUnit.MILLISECONDS) < 1) return Status.BAD_REQUEST
              buffer.limit(buffer.position())
            }
            buffer.position(start + chunkSize)
            // chunk bytes should be followed by \r\n
            if (buffer.get() != CR || buffer.get() != LF) return Status.BAD_REQUEST
            if (chunkSize == 0) {
              // zero length chunk marks the end of the chunk list
              // skip trailing fields (look for \r\n\r\n sequence)
              val last = buffer.position() - 2
              if (last > buffer.capacity() - 4) return Status.PAYLOAD_TOO_LARGE
              buffer.limit(limit)
              while (true) {
                val limit = buffer.limit()
                if (buffer.remaining() > 1) {
                  if (buffer[limit - 1] == LF && buffer[limit - 2] == CR &&
                      buffer[limit - 3] == LF && buffer[limit - 4] == CR) break
                }
                buffer.position(limit).limit(buffer.capacity())
                if (socket.aRead(buffer, 3000L, TimeUnit.MILLISECONDS) < 1) return Status.BAD_REQUEST
                buffer.limit(buffer.position()).position(limit)
              }
              buffer.limit(last).position(0)
              break@chunks
            }
            start = buffer.position() - 2
            break@bytes
          }
          sb.append(b.toChar())
        }
      }
    }
    if (bodyRequired && buffer.remaining() == 0) return Status.BAD_REQUEST
    return null
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
