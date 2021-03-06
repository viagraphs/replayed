package com.pragmaxim

import monifu.reactive.Ack
import monifu.reactive.Ack.Continue
import org.scalajs.dom.ext.PimpedHtmlCollection
import org.scalajs.dom.html.{Div, Span}
import org.scalajs.dom.raw._

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

package object replayed {

  implicit class FutureExt[T](f: Future[T]) {
    
    def mapSuccessToContinue(implicit ec: ExecutionContext) = {
      val p = Promise[Ack]()
      f onComplete {
        case Success(_) => p.success(Continue)
        case Failure(ex) => p.failure(ex)
      }
      p.future
    }
    
  }
  
  implicit class RichNode(n: Node) {

    def removeAllChildren(): List[Node] = {
      val removed = ArrayBuffer[Node]()
      var lastChild = n.lastChild
      while (lastChild != null) {
        removed.append(n.removeChild(lastChild))
        lastChild = n.lastChild
      }
      removed.toList
    }

    @tailrec
    final def childIdx(cur: Node = n, i: Int = 0): Int = {
      Option(cur.previousSibling) match {
        case Some(prev) if prev.nodeType == 1 =>
          childIdx(prev, i + 1)
        case None => i
        case _ => i
      }
    }

    def lineTextNodeByIndex(): (Int, Node) = n match {
      case text if text.nodeType == 3 =>
        val line = text.parentNode.parentNode
        require(line.asInstanceOf[Div].className == "e_line")
        (childIdx(line), text)
      // unfortunately one cannot set end offset 0 of next text node, DOM API makes it LINE
      case line: Div if line.className == "e_line" =>
        (childIdx(line), Option(line.firstElementChild.firstChild).getOrElse(line.firstElementChild))
      case span: Span if span.className == "e_text" =>
        (childIdx(span.parentNode), Option(span.firstChild).getOrElse(span))
      case x => throw new IllegalStateException("Please use lineIdx method on lines only, not : " + x)
    }

    @tailrec
    final def forAncestorWithClass(name: String*)(fn: HTMLElement => Unit): Unit = {
      n match {
        case node: HTMLElement =>
          if (name.count(node.classList.contains) == name.length)
            fn(node)
          else if (Option(n.parentNode).isDefined)
            n.parentNode.forAncestorWithClass(name:_*)(fn)
        case _ =>
      }
    }
  }

  implicit class RichHTMLElement(e: Element) {

    def hasClass(name: String): Boolean = e.classList.contains(name)

    def markup[T <: HTMLElement](fn: T => Unit) = {
      val htmlElement = e.asInstanceOf[T]
      fn(htmlElement)
      htmlElement
    }

    def styleChildren(fn: CSSStyleDeclaration => Unit): Unit = {
      e.children.foreach {
        case he: HTMLElement => fn(he.style)
      }
    }
  }
}
