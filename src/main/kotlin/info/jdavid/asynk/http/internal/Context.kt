package info.jdavid.asynk.http.internal

import java.nio.ByteBuffer

interface Context {

  var buffer: ByteBuffer?

  val maxRequestSize: Int

}
