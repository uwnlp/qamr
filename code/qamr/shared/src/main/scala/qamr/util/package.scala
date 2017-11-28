package qamr

import cats.Order
import cats.Foldable
import cats.Reducible
import cats.Traverse
import cats.data.NonEmptyList
import cats.data.State
import cats.implicits._

import nlpdata.util.LowerCaseStrings._
import scala.util.{Try, Success, Failure}

import scala.language.implicitConversions

/** Provides miscellaneous utility classes and methods, primarily for data analysis. */
package object util extends PackagePlatformExtensions {

  /** We require questions to begin with one of these words. */
  val whWords = Set("who", "what", "when", "where", "why", "how", "which", "whose").map(_.lowerCase)

  def beginsWithWhSpace(s: String): Boolean = whWords.exists(w => s.lowerCase.startsWith(w + " ".lowerCase))

  def dollarsToCents(d: Double): Int = math.round(100 * d).toInt

  def const[A](a: A): Any => A = _ => a

  implicit class RichFoldable[F[_]: Foldable, A](val fa: F[A]) {

    def meanOpt(implicit N: Numeric[A]): Option[Double] = {
      val (sum, count) = fa.foldLeft(N.fromInt(0), N.fromInt(0)) {
        case ((curSum, curCount), a) => (N.plus(curSum, a), N.plus(curCount, N.fromInt(1)))
      }
      if(count == 0) None else Some(N.toDouble(sum) / N.toDouble(count))
    }

  }

}
