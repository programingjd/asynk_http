package info.jdavid.asynk.http

import java.io.File

/**
 * MediaTypes (Content-Types with no charset information).
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
object MediaType {

  const val CSS = "text/css"
  const val CSV = "text/csv"
  const val HTML = "text/html"
  const val TEXT = "text/plain"
  const val URI_LIST = "text/uri-list"
  const val XML = "text/xml"

  const val ATOM = "application/atom+xml"
  const val GZIP = "application/gzip"
  const val TAR = "application/x-gtar"
  const val XZ = "application/z-xz"
  const val SEVENZ = "application/x-7z-compressed"
  const val ZIP = "application/zip"
  const val RAR = "application/vnd.rar"
  const val JAR = "application/java-archive"
  const val APK = "application/vnd.android.package-archive"
  const val DMG = "application/x-apple-diskimage"
  const val ISO = "application/x-iso9660-image"
  const val JAVASCRIPT = "application/javascript"
  const val JSON = "application/json"
  const val OCTET_STREAM = "application/octet-stream"
  const val PDF = "application/pdf"
  const val WOFF = "application/font-woff"
  const val WOFF2 = "font/woff2"
  const val XHTML = "application/xhtml+xml"
  const val WEB_MANIFEST = "application/manifest+json"

  const val OTF = "application/opentype"
  const val TTF = "application/truetype"
  const val EOT = "application/vnd.ms-fontobject"

  const val WAV = "audio/x-wav"
  const val MP3 = "audio/mpeg"
  const val OGG = "audio/ogg"

  const val MP4 = "video/mp4"
  const val OGV = "video/ogg"
  const val WEBM = "video/webm"

  const val EMAIL = "message/rfc822"

  const val BYTE_RANGES = "multipart/byteranges"
  const val DIGEST = "multipart/digest"
  const val FORM_DATA = "multipart/form-data"
  const val MIXED = "multipart/mixed"
  const val SIGNED = "multipart/signed"
  const val ENCRYPTED = "multipart/encrypted"

  const val JPG = "image/jpeg"
  const val PNG = "image/png"
  const val GIF = "image/gif"
  const val BMP = "image/bmp"
  const val SVG = "image/svg+xml"
  const val ICO = "image/x-icon"
  const val WEBP = "image/webp"

  const val SSE = "text/event-stream"

  const val DIRECTORY = "text/directory"

  private val map = mapOf(
    "html" to HTML,
    "css" to CSS,
    "js" to JAVASCRIPT,
    "jpg" to JPG,
    "png" to PNG,
    "htm" to HTML,
    "xhtml" to XHTML,
    "woff" to WOFF,
    "woff2" to WOFF2,
    "webmanifest" to WEB_MANIFEST,
    "otf" to OTF,
    "ttf" to TTF,
    "eot" to EOT,
    "gif" to GIF,
    "bmp" to BMP,
    "svg" to SVG,
    "ico" to ICO,
    "webp" to WEBP,

    "csv" to CSV,
    "txt" to TEXT,
    "howto" to TEXT,
    "readme" to TEXT,
    "xml" to XML,
    "xsl" to XML,
    "gz" to GZIP,
    "tar" to TAR,
    "xz" to XZ,
    "7z" to SEVENZ,
    "zip" to ZIP,
    "rar" to RAR,
    "jar" to JAR,
    "apk" to APK,
    "dmg" to DMG,
    "iso" to ISO,
    "json" to JSON,
    "bin" to OCTET_STREAM,
    "pdf" to PDF,

    "wav" to WAV,
    "mp3" to MP3,
    "ogg" to OGG,
    "mp4" to MP4,
    "ogv" to OGV,
    "webm" to WEBM
  )

  /**
   * Tries to identify the media type of the resource represented by the specified uri.
   * @param uri the uri.
   * @return the media type, or null if it could not be identified.
   */
  fun fromUri(uri: String): String? {
    val path = Uri.path(uri)
    if (path.last() == '/') return DIRECTORY
    val filename = path.substring(path.lastIndexOf('/'))
    val i = filename.lastIndexOf('.')
    if (i == -1) return null
    val ext = filename.substring(i+1).toLowerCase()
    return map[ext]
  }

  /**
   * Tries to identify the media type of the resource represented by the specified file.
   * @param file the file.
   * @return the media type, or null if it could not be identified.
   */
  fun fromFile(file: File): String? {
    if (file.isDirectory) return DIRECTORY
    val filename = file.name
    if ("." == filename || ".." == filename) return DIRECTORY
    val i = filename.lastIndexOf('.')
    if (i == -1) {
      if (!file.isFile) return DIRECTORY
      val parent = file.parentFile
      return if (parent != null && "acme-challenge" == parent.name) TEXT else null
    }
    val ext = filename.substring(i+1).toLowerCase()
    return map[ext]
  }

}
