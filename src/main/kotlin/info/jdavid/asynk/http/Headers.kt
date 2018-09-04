package info.jdavid.asynk.http

/**
 * HTTP Headers.<br>
 *   Field-1: value1\r\n
 *   Field-2: value2\r\n
 *   ...
 */
@Suppress("MemberVisibilityCanBePrivate")
class Headers(internal val lines: MutableList<String> = ArrayList(16)) {

  /**
   * Returns the ordered list of header lines.
   * @return the list of header lines.
   */
  fun lines(): List<String> = lines

  /**
   * Adds a header line.
   * @param name the header field name.
   * @param value the header value.
   * @return this.
   */
  fun add(name: String, value: String): Headers {
    lines.add("${name}: ${value}")
    return this
  }

  internal fun clear(): Headers {
    lines.clear()
    return this
  }

  internal fun unset(name: String): Headers {
    val lower = name.toLowerCase()
    lines.removeIf { matches(it, lower) }
    return this
  }

  internal fun set(line: String): Headers {
    val key = line.substring(0, line.indexOf(':'))
    unset(key)
    lines.add(line)
    return this
  }

  /**
   * Sets a header field value (same as [add] but removes any previous header line for the same field if there
   * are any).
   * @param name the header field name.
   * @param value the header value.
   * @return this.
   */
  fun set(name: String, value: String): Headers {
    unset(name)
    add(name, value)
    return this
  }

  /**
   * Returns the header field value. If there are multiple header lines for the same field, the value of
   * the last one is returned. If there are no header line for that field, then null is returned.
   * @param name the header field name.
   * @return the last header value or null if the header is missing.
   */
  fun value(name: String): String? {
    val lower = name.toLowerCase()
    return lines.findLast { matches(it, lower) }?.substring(name.length + 1)?.trim()
  }

  /**
   * Returns the (ordered) list of values for a header field.
   * @param name the header field name.
   * @return the list of values (can be empty).
   */
  fun values(name: String): List<String> {
    val lower = name.toLowerCase()
    return lines.filter { matches(it, lower) }.map { it.substring(name.length + 1).trim() }
  }

  /**
   * Returns a list of header field names (in lower case, without duplicates).
   * @return the list of header field names present in the headers.
   */
  fun keys(): List<String> {
    val set = HashSet<String>(lines.size)
    val list = ArrayList<String>(lines.size)
    lines.forEach {
      val key = it.substring(0, it.indexOf(':'))
      if (set.add(key.toLowerCase())) list.add(key)
    }
    return list
  }

  /**
   * Returns whether a given header field is present in the headers or not.
   * @return true if the header field is present, false if it isn't.
   */
  fun has(name: String): Boolean {
    val lower = name.toLowerCase()
    return lines.find { matches(it, lower) } != null
  }

  @Suppress("unused")
  companion object {
    const val WWW_AUTHENTICATE = "WWW-Authenticate"
    const val AUTHORIZATION = "Authorization"

    const val AGE = "Age"
    const val CACHE_CONTROL = "Cache-Control"
    const val EXPIRES = "Expires"
    const val PRAGMA = "Pragma"
    const val WARNING = "Warning"

    const val LAST_MODIFIED = "Last-Modified"
    const val ETAG = "ETag"
    const val IF_MATCH = "If-Match"
    const val IF_NONE_MATCH = "If-None-Match"
    const val IF_MODIFIED_SINCE = "If-Modified-Since"
    const val IF_UNMODIFIED_SINCE = "If-Unmodified-Since"

    const val CONNECTION = "Connection"
    const val KEEP_ALIVE = "Keep-Alive"

    const val ACCEPT = "Accept"
    const val ACCEPT_CHARSET = "Accept-Charset"
    const val ACCEPT_ENCODING = "Accept-Encoding"
    const val ACCEPT_LANGUAGE = "Accept-Language"
    const val ACCEPT_PATCH = "Accept-Patch"

    const val EXPECT = "Expect"

    const val COOKIE = "Cookie"
    const val SET_COOKIE = "Set-Cookie"

    const val ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin"
    const val ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials"
    const val ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers"
    const val ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods"
    const val ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers"
    const val ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age"
    const val ACCESS_CONTROL_REQUEST_HEADERS = "Access-Control-Request-Headers"
    const val ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method"
    const val ORIGIN = "Origin"
    const val TIMING_ALLOW_ORIGIN = "Timing-Allow-Origin"

    const val DO_NOT_TRACK = "DNT"
    const val DNT = DO_NOT_TRACK
    const val TRACKING_STATUS = "Tk"
    const val Tk = TRACKING_STATUS

    const val CONTENT_DISPOSITION = "Content-Disposition"

    const val CONTENT_LENGTH = "Content-Length"
    const val CONTENT_TYPE = "Content-Type"
    const val CONTENT_ENCODING = "Content-Encoding"
    const val CONTENT_LANGUAGE = "Content-Language"
    const val CONTENT_LOCATION = "Content-Location"

    const val FORWARDED = "Forwarded"
    const val X_FORWARDED_WITH = "X-Forwarded-With"
    const val X_FORWARDED_FOR = "X-Forwarded-For"
    const val X_FORWARDED_HOST = "X-Forwarded-Host"
    const val X_FORWARDED_PROTO = "X-Forwarded-Proto"
    const val VIA = "Via"

    const val LOCATION = "Location"

    const val FROM = "From"
    const val HOST = "Host"
    const val REFERER = "Referer"
    const val REFERRER = REFERER
    const val REFERRER_POLICY = "Referrer-Policy"
    const val USER_AGENT = "User-Agent"

    const val ALLOW = "Allow"
    const val SERVER = "Server"

    const val ACCEPT_RANGES = "Accept-Ranges"
    const val RANGE = "Range"
    const val IF_RANGE = "If-Range"
    const val CONTENT_RANGE = "Content-Range"

    const val CONTENT_SECURITY_POLICY = "Content-Security-Policy"
    const val CSP = CONTENT_SECURITY_POLICY
    const val PUBLIC_KEY_PINS = "Public-Key-Pins"
    const val HPKP = PUBLIC_KEY_PINS
    const val STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security"
    const val HSTS = STRICT_TRANSPORT_SECURITY
    const val UPGRADE_INSECURE_REQUESTS = "Upgrade-Insecure-Requests"
    const val X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options"
    const val X_FRAME_OPTIONS = "X-Frame-Options"
    const val X_XSS_PROTECTION = "X-XSS-Protection"

    const val TRANSFER_ENCODING = "Transfer-Encoding"
    const val TE = "TE"
    const val TRAILER = "Trailer"

    const val DATE = "Date"
    const val LARGE_ALLOCATION = "Large-Allocation"
    const val LINK = "Link"
    const val RETRY_AFTER = "Retry-After"
    const val SOURCE_MAP = "SourceMap"
    const val UPGRADE = "Upgrade"
    const val VARY = "Vary"
    const val X_DNS_PREFETCH_CONTROL = "X-DNS-Prefetch-Control"

    private fun matches(line: String, lowercaseName: String): Boolean {
      return line.length > lowercaseName.length + 1 &&
             line.substring(0, lowercaseName.length).toLowerCase() == lowercaseName &&
             line[lowercaseName.length] == ':'
    }
  }

}
