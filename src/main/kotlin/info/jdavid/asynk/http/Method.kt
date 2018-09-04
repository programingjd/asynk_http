package info.jdavid.asynk.http

/**
 * HTTP Methods.
 */
sealed class Method {

  override fun toString(): String {
    return javaClass.simpleName
  }

  object OPTIONS: Method()
  object HEAD: Method()
  object GET: Method()
  object POST: Method()
  object PUT: Method()
  object DELETE: Method()
  object PATCH: Method()

  @Suppress("unused")
  data class Custom(val name: String): Method()

  companion object {
    fun from(m: String): Method {
      return when(m) {
        "OPTIONS" -> OPTIONS
        "HEAD" -> HEAD
        "GET" -> GET
        "POST" -> POST
        "PUT" -> PUT
        "DELETE" -> DELETE
        "PATCH" -> PATCH
        else -> throw RuntimeException("Unexpected HTTP method: ${m}")
      }
    }
  }

}
