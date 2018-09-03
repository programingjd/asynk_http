package info.jdavid.asynk.http

/**
 * HTTP Status Codes.
 */
class Status private constructor() {

  @Suppress("unused")
  companion object {

    const val CONTINUE = 100
    const val SWITCHING_PROTOCOLS = 100
    const val OK = 200
    const val CREATED = 201
    const val ACCEPTED = 202
    const val NO_CONTENT = 204
    const val PARTIAL_CONTENT = 206
    const val MOVED_PERMANENTLY = 301
    const val FOUND = 302
    const val SEE_OTHER = 303
    const val NOT_MODIFIED = 304
    const val TEMPORARY_REDIRECT = 307
    const val PERMANENT_REDIRECT = 308
    const val BAD_REQUEST = 400
    const val UNAUTHORIZED = 401
    const val FORBIDDEN = 403
    const val NOT_FOUND = 404
    const val METHOD_NOT_ALLOWED = 405
    const val NOT_ACCEPTABLE = 406
    const val REQUEST_TIMEOUT = 408
    const val CONFLICT = 409
    const val GONE = 410
    const val PRECONDITION_FAILED = 412
    const val PAYLOAD_TOO_LARGE = 413
    const val URI_TOO_LONG = 414
    const val UNSUPPORTED_MEDIA_TYPE = 415
    const val REQUESTED_RANGE_NOT_SATISFIABLE = 416
    const val EXPECTATION_FAILED = 417
    const val PRECONDITION_REQUIRED = 428
    const val TOO_MANY_REQUESTS = 429
    const val REQUEST_HEADER_FIELDS_TOO_LARGE = 431
    const val INTERNAL_SERVER_ERROR = 500
    const val NOT_IMPLEMENTED = 501
    const val BAD_GATEWAY = 502
    const val SERVICE_UNAVAILABLE = 503
    const val GATEWAY_TIMEOUT = 504

    val HTTP_STATUSES = mapOf(
      100 to "Continue",
      101 to "Switching Protocols",
      200 to "OK",
      201 to "Created",
      202 to "Accepted",
      204 to "No Content",
      206 to "Partial Content",
      301 to "Moved Permanently",
      302 to "Found",
      303 to "See Other",
      304 to "Not Modified",
      307 to "Temporary Redirect",
      308 to "Permanent Redirect",
      400 to "Bad Request",
      401 to "Unauthorized",
      403 to "Forbidden",
      404 to "Not Found",
      405 to "Method Not Allowed",
      406 to "Not Acceptable",
      408 to "Request Timeout",
      409 to "Conflict",
      410 to "Gone",
      412 to "Precondition Failed",
      413 to "Payload Too Large",
      414 to "URI Too Long",
      415 to "Unsupported Media Type",
      416 to "Requested Range Not Satisfiable",
      417 to "Expectation Failed",
      428 to "Precondition Required",
      429 to "Too Many Requests",
      431 to "Request Header Fields Too Large",
      500 to "Internal Server Error",
      501 to "Not Implemented",
      502 to "Bad Gateway",
      503 to "Service Unavailable",
      504 to "Gateway Timeout"
    )

    internal fun message(status: Int) = HTTP_STATUSES[status]

  }

}
