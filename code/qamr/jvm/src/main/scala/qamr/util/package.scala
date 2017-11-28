package qamr.util

import java.io.StringWriter
import java.io.PrintWriter

import scala.util.{Try, Success, Failure}

import com.typesafe.scalalogging.Logger

trait PackagePlatformExtensions {
  implicit class RichTry[A](val t: Try[A]) {
    def toOptionLogging(logger: Logger): Option[A] = t match {
      case Success(a) =>
        Some(a)
      case Failure(e) =>
        val sw = new StringWriter()
        val pw = new PrintWriter(sw, true)
        e.printStackTrace(pw)
        logger.error(e.getLocalizedMessage + "\n" + sw.getBuffer.toString)
        None
    }
  }

}
