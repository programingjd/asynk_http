package info.jdavid.asynk.http

/**
 * Request Uniform Resource Identifier.<br>
 * scheme://authority/path_fragment1/path_fragment2#fragment?query_param1=value1&query_param2
 */
object Uri {

  /**
   * Extracts the query parameter and values from the uri and returns it as a map of key/value.
   * @param uri the uri.
   * @return the map or null if the uri doesn't have a query.
   */
  fun query(uri: String): Map<String,String>? {
    val qmark = uri.indexOf('?', 1)
    if (qmark == -1) return null
    val hash = uri.indexOf('#', qmark + 1)
    val q = if (hash == -1) uri.substring(qmark + 1) else uri.substring(qmark + 1, hash)
    return q.split('&').let {
      val map = LinkedHashMap<String, String>(it.size)
      it.forEach {
        if (it.isNotEmpty()) {
          it.split('=').apply {
            when (size) {
              0 -> map[""] = ""
              1 -> map[this[0]] = ""
              else -> map[this[0]] = this[1]
            }
          }
        }
      }
      map
    }
  }

  /**
   * Returns the fragment (or hash) of the uri.
   * @param uri the uri.
   * @return the fragment/hash if any, null otherwise.
   */
  fun fragment(uri: String): String? {
    val hash = uri.indexOf('#', 1)
    return if (hash == -1) null else uri.substring(hash + 1)
  }

  /**
   * Returns the (encoded) path of the uri.
   * @param uri the uri.
   * @return the encoded path (it can be empty).
   */
  fun path(uri: String): String {
    val stop = uri.indexOf('?', 1)
    val end = if (stop == -1) uri.indexOf('#', 1) else stop
    return if (end == -1) uri else uri.substring(0, end)
  }

  /**
   * Returns the uri of the parent resource (drops the last path segment).
   * @param uri the uri.
   * @return the uri of the parent resource.
   */
  fun parent(uri: String): String {
    val stop = uri.indexOf('?', 1)
    val end = if (stop == -1) uri.indexOf('#', 1) else stop
    val slash = if (end == -1) uri.lastIndexOf('/') else uri.lastIndexOf('/', end - 1)
    return if (end == -1) uri.substring(0, slash + 1) else uri.substring(0, slash + 1) + uri.substring(end)
  }

  /**
   * Returns the uri last path segment (encoded).
   * @param uri the uri.
   * @return the last encoded path segment (can be empty).
   */
  fun lastPathSegment(uri: String): String {
    val stop = uri.indexOf('?', 1)
    val end = if (stop == -1) uri.indexOf('#', 1) else stop
    val slash = if (end == -1) uri.lastIndexOf('/') else uri.lastIndexOf('/', end - 1)
    return if (end == -1) uri.substring(slash + 1) else uri.substring(slash + 1, end)
  }

}
