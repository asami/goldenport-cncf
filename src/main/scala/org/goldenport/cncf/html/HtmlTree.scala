package org.goldenport.cncf.html

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.mutable.ArrayBuffer
import scala.util.matching.Regex
import org.goldenport.Consequence

/*
 * @since   Apr. 29, 2026
 * @version Apr. 29, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait HtmlNode {
  def render: String
  def textContent: String
  def elements: Vector[HtmlElement]
  def mapElements(f: HtmlElement => HtmlElement): HtmlNode
}

final case class HtmlDocument(nodes: Vector[HtmlNode]) {
  def title: Option[String] =
    headElements("title").headOption.map(_.textContent.trim).filter(_.nonEmpty)

  def description: Option[String] =
    headElements("meta")
      .find(_.attr("name").exists(_.equalsIgnoreCase("description")))
      .flatMap(_.attr("content"))
      .map(_.trim)
      .filter(_.nonEmpty)

  def canonical: Option[String] =
    headElements("link")
      .find(_.attr("rel").exists(_.split("\\s+").exists(_.equalsIgnoreCase("canonical"))))
      .flatMap(_.attr("href"))
      .map(_.trim)
      .filter(_.nonEmpty)

  def articleFragment: Consequence[HtmlFragment] =
    selectArticle.map(x => HtmlFragment(x.children))

  def selectArticle: Consequence[HtmlElement] =
    findElement(e => e.name == "article" && e.attributes.contains("data-blog-content"))
      .orElse(findElement(_.name == "article"))
      .orElse(findElement(e => e.name == "main").flatMap(_.findElement(_.name == "article")))
      .map(Consequence.success)
      .getOrElse(Consequence.operationInvalid("HTML document does not contain an article element"))

  def findElement(p: HtmlElement => Boolean): Option[HtmlElement] =
    elements.find(p)

  def headElements(name: String): Vector[HtmlElement] =
    findElement(_.name == "head").map(_.elements.filter(_.name == name.toLowerCase)).getOrElse(Vector.empty)

  def elements: Vector[HtmlElement] =
    nodes.flatMap(_.elements)

  def render: String =
    nodes.map(_.render).mkString
}

final case class HtmlFragment(nodes: Vector[HtmlNode]) {
  def render: String =
    nodes.map(_.render).mkString

  def images: Vector[HtmlImageOccurrence] = {
    var index = -1
    elements.collect {
      case e if e.name == "img" && e.attr("src").exists(_.nonEmpty) =>
        index = index + 1
        HtmlImageOccurrence(
          index,
          e.attr("src").getOrElse(""),
          e.attr("alt"),
          e.attr("title")
        )
    }
  }

  def rewriteImageSources(f: HtmlImageOccurrence => Option[String]): HtmlFragment = {
    var index = -1
    def rewrite(node: HtmlNode): HtmlNode = node match {
      case e: HtmlElement =>
        val rewrittenChildren = e.children.map(rewrite)
        val withChildren = e.copy(children = rewrittenChildren)
        if (withChildren.name == "img" && withChildren.attr("src").exists(_.nonEmpty)) {
          index = index + 1
          val occurrence = HtmlImageOccurrence(
            index,
            withChildren.attr("src").getOrElse(""),
            withChildren.attr("alt"),
            withChildren.attr("title")
          )
          f(occurrence)
            .map(src => withChildren.copy(attributes = withChildren.attributes + ("src" -> src)))
            .getOrElse(withChildren)
        } else {
          withChildren
        }
      case other => other
    }
    HtmlFragment(nodes.map(rewrite))
  }

  def elements: Vector[HtmlElement] =
    nodes.flatMap(_.elements)
}

final case class HtmlImageOccurrence(
  index: Int,
  src: String,
  alt: Option[String],
  title: Option[String]
)

final case class HtmlElement(
  name: String,
  attributes: Map[String, String] = Map.empty,
  children: Vector[HtmlNode] = Vector.empty
) extends HtmlNode {
  def attr(name: String): Option[String] =
    attributes.get(name.toLowerCase)

  def render: String = {
    val attrs = attributes.toVector.sortBy(_._1).map { case (k, v) =>
      s""" ${HtmlTree.escapeAttribute(k)}="${HtmlTree.escapeAttribute(v)}""""
    }.mkString
    if (HtmlTree.isVoidElement(name))
      s"<${name}${attrs}>"
    else
      s"<${name}${attrs}>${children.map(_.render).mkString}</${name}>"
  }

  def textContent: String =
    children.map(_.textContent).mkString

  def elements: Vector[HtmlElement] =
    this +: children.flatMap(_.elements)

  def findElement(p: HtmlElement => Boolean): Option[HtmlElement] =
    elements.find(p)

  def mapElements(f: HtmlElement => HtmlElement): HtmlNode =
    f(copy(children = children.map(_.mapElements(f))))
}

final case class HtmlText(text: String) extends HtmlNode {
  def render: String =
    HtmlTree.escapeText(text)

  def textContent: String =
    text

  def elements: Vector[HtmlElement] =
    Vector.empty

  def mapElements(f: HtmlElement => HtmlElement): HtmlNode =
    this
}

final case class HtmlComment(text: String) extends HtmlNode {
  def render: String =
    s"<!--${text.replace("--", "- -")}-->"

  def textContent: String =
    ""

  def elements: Vector[HtmlElement] =
    Vector.empty

  def mapElements(f: HtmlElement => HtmlElement): HtmlNode =
    this
}

object HtmlTree {
  def parse(text: String): Consequence[HtmlDocument] =
    try {
      Consequence.success(new _HtmlParser(text).parse())
    } catch {
      case e: Exception =>
        Consequence.operationInvalid(s"failed to parse HTML document: ${e.getMessage}")
    }

  def parseFile(path: Path): Consequence[HtmlDocument] =
    try {
      parse(Files.readString(path, StandardCharsets.UTF_8))
    } catch {
      case e: Exception =>
        Consequence.operationInvalid(s"failed to read HTML document: ${path}: ${e.getMessage}")
    }

  def escapeText(p: String): String =
    p
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")

  def escapeAttribute(p: String): String =
    escapeText(p).replace("\"", "&quot;")

  def decodeText(p: String): String =
    p
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .replace("&apos;", "'")
      .replace("&amp;", "&")

  def isVoidElement(name: String): Boolean =
    _void_elements.contains(name.toLowerCase)

  private val _void_elements: Set[String] = Set(
    "area",
    "base",
    "br",
    "col",
    "embed",
    "hr",
    "img",
    "input",
    "link",
    "meta",
    "param",
    "source",
    "track",
    "wbr"
  )

  private final class _MutableElement(
    val name: String,
    val attributes: Map[String, String]
  ) {
    val children: ArrayBuffer[HtmlNode] = ArrayBuffer.empty

    def toElement: HtmlElement =
      HtmlElement(name, attributes, children.toVector)
  }

  private final class _HtmlParser(source: String) {
    private val _roots = ArrayBuffer.empty[HtmlNode]
    private val _stack = ArrayBuffer.empty[_MutableElement]
    private var _offset = 0

    def parse(): HtmlDocument = {
      while (_offset < source.length) {
        if (_starts_with("<!--"))
          _read_comment()
        else if (_starts_with("</"))
          _read_end_tag()
        else if (_starts_with("<!"))
          _skip_declaration()
        else if (_starts_with("<"))
          _read_start_tag()
        else
          _read_text()
      }
      while (_stack.nonEmpty) {
        val element = _stack.remove(_stack.length - 1).toElement
        if (_stack.isEmpty)
          _roots += element
        else
          _stack.last.children += element
      }
      HtmlDocument(_roots.toVector)
    }

    private def _append(node: HtmlNode): Unit =
      if (_stack.isEmpty)
        _roots += node
      else
        _stack.last.children += node

    private def _close_until(name: String): Unit = {
      val index = _stack.lastIndexWhere(_.name == name)
      if (index >= 0) {
        while (_stack.length > index) {
          val element = _stack.remove(_stack.length - 1).toElement
          if (_stack.isEmpty)
            _roots += element
          else
            _stack.last.children += element
        }
        }
      }

    private def _read_comment(): Unit = {
      val end = source.indexOf("-->", _offset + 4)
      if (end < 0) {
        _append(HtmlComment(source.substring(_offset + 4)))
        _offset = source.length
      } else {
        _append(HtmlComment(source.substring(_offset + 4, end)))
        _offset = end + 3
      }
    }

    private def _skip_declaration(): Unit =
      _offset = _tag_end(_offset + 2).map(_ + 1).getOrElse(source.length)

    private def _read_end_tag(): Unit = {
      val end = _tag_end(_offset + 2).getOrElse(source.length)
      val name = source.substring(_offset + 2, end).trim.takeWhile(c => !c.isWhitespace && c != '>').toLowerCase
      if (name.nonEmpty)
        _close_until(name)
      _offset = math.min(end + 1, source.length)
    }

    private def _read_start_tag(): Unit = {
      val end = _tag_end(_offset + 1).getOrElse(source.length)
      val raw = source.substring(_offset + 1, end).trim
      if (raw.nonEmpty && !raw.startsWith("/")) {
        val selfClosing = raw.endsWith("/")
        val body = if (selfClosing) raw.dropRight(1).trim else raw
        val name = body.takeWhile(c => !c.isWhitespace && c != '/' && c != '>').toLowerCase
        if (name.nonEmpty) {
          val attrText = body.drop(name.length)
          val attrs = _attributes(attrText)
          val element = new _MutableElement(name, attrs)
          if (selfClosing || isVoidElement(name))
            _append(element.toElement)
          else
            _stack += element
        }
      }
      _offset = math.min(end + 1, source.length)
    }

    private def _read_text(): Unit = {
      val next = source.indexOf('<', _offset)
      val end = if (next < 0) source.length else next
      if (end > _offset)
        _append(HtmlText(decodeText(source.substring(_offset, end))))
      _offset = end
    }

    private def _starts_with(prefix: String): Boolean =
      source.startsWith(prefix, _offset)

    private def _tag_end(start: Int): Option[Int] = {
      var i = start
      var quote: Char = 0
      while (i < source.length) {
        val c = source.charAt(i)
        if (quote == 0 && (c == '"' || c == '\''))
          quote = c
        else if (quote != 0 && c == quote)
          quote = 0
        else if (quote == 0 && c == '>')
          return Some(i)
        i = i + 1
      }
      None
    }

    private def _attributes(text: String): Map[String, String] =
      _attribute_pattern
        .findAllMatchIn(text)
        .map { m =>
          val value =
            Option(m.group(2))
              .orElse(Option(m.group(3)))
              .orElse(Option(m.group(4)))
              .getOrElse("")
          m.group(1).toLowerCase -> decodeText(value)
        }
        .toMap

    private val _attribute_pattern: Regex =
      """([A-Za-z_:][-A-Za-z0-9_:.]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?""".r
  }
}
