package info.jdavid.asynk.http

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class UriTests {

  @Test fun testQuery() {
    assertNull(Uri.query("/"))
    assertNull(Uri.query("/some/path"))
    assertNull(Uri.query("/some/path/"))
    assertNull(Uri.query("/some/path#fragment"))
    assertEquals(0, Uri.query("/some/path?")?.size)
    assertEquals(0, Uri.query("/some/path?#fragment")?.size)
    assertEquals("", Uri.query("/some/path?1")?.get("1"))
    assertEquals("d", Uri.query("/some/path/?abc=d")?.get("abc"))
    assertEquals("", Uri.query("/some/path?q#fragment")?.get("q"))
    assertNull(Uri.query("/some/path?q#fragment")?.get("abc"))
    assertEquals("1", Uri.query("/some/path?a=1&b=2#fragment")?.get("a"))
    assertEquals("2", Uri.query("/some/path?a=1&b=2#fragment")?.get("b"))
  }

  @Test fun testFragment() {
    assertNull(Uri.fragment("/"))
    assertNull(Uri.fragment("/some/path"))
    assertNull(Uri.fragment("/some/path/"))
    assertNull(Uri.fragment("/some/path?query"))
    assertEquals("", Uri.fragment("/some/path#"))
    assertEquals("", Uri.fragment("/some/path/#"))
    assertEquals("", Uri.fragment("/some/path?query#"))
    assertEquals("abc", Uri.fragment("/some/path#abc"))
    assertEquals("1", Uri.fragment("/some/path/#1"))
    assertEquals("@here", Uri.fragment("/some/path?query#@here"))
  }

  @Test fun testPath() {
    assertEquals("/", Uri.path("/"))
    assertEquals("/some/path", Uri.path("/some/path"))
    assertEquals("/some/path/", Uri.path("/some/path/"))
    assertEquals("/some/path", Uri.path("/some/path?query"))
    assertEquals("/some/path/", Uri.path("/some/path/?query#fragment"))
    assertEquals("/", Uri.path("/?query#f1/f2"))
  }

  @Test fun testParent() {
    assertEquals("/", Uri.parent("/a"))
    assertEquals("/a/", Uri.parent("/a/b"))
    assertEquals("/?q=1", Uri.parent("/a?q=1"))
    assertEquals("/#/p1/p2", Uri.parent("/a#/p1/p2"))
    assertEquals("/a/?q=1#h", Uri.parent("/a/b?q=1#h"))
  }

  @Test fun testLastPathSegment() {
    assertEquals("", Uri.lastPathSegment("/"))
    assertEquals("", Uri.lastPathSegment("/a/"))
    assertEquals("a", Uri.lastPathSegment("/a"))
    assertEquals("b", Uri.lastPathSegment("/a/b"))
    assertEquals("a", Uri.lastPathSegment("/a?q=1"))
    assertEquals("a", Uri.lastPathSegment("/a#/p1/p2"))
    assertEquals("b", Uri.lastPathSegment("/a/b?q=1#h"))
  }

}
