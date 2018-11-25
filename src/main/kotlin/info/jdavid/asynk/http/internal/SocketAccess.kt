package info.jdavid.asynk.http.internal

import info.jdavid.asynk.core.asyncRead
import info.jdavid.asynk.core.asyncWrite
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

interface SocketAccess {

  suspend fun asyncRead(socket: AsynchronousSocketChannel, buffer: ByteBuffer): Long

  suspend fun asyncWrite(socket: AsynchronousSocketChannel, buffer: ByteBuffer): Long

  object Raw: SocketAccess {
    override suspend fun asyncRead(socket: AsynchronousSocketChannel, buffer: ByteBuffer) =
      socket.asyncRead(buffer)
    override suspend fun asyncWrite(socket: AsynchronousSocketChannel, buffer: ByteBuffer) =
      socket.asyncWrite(buffer)
  }

}
