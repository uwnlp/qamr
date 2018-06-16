package qamr

import cats.Foldable
import cats.Reducible
import cats.data.NonEmptyList
import cats.implicits._

package object analysis {
  def pctString(num: Int, denom: Int): String =
    f"$num%d (${num * 100.0 / denom}%.2f%%)"
  def distString[F[_]: Reducible, N](iter: F[N])(implicit N : Numeric[N]): String =
    f"${N.toDouble(iter.sum)}%.2f (${iter.mean}%.2f ± ${iter.stdev}%.4f)"
  def noSumDistString[F[_]: Reducible, N](iter: F[N])(implicit N : Numeric[N]): String =
    f"${iter.mean}%.2f ± ${iter.stdev}%.4f"

  implicit class RichReducible[F[_]: Reducible, A](val a: F[A]) {

    def head: A = a.reduceLeft { case (a, _) => a }

    def last: A = a.reduceLeft { case (_, a) => a }

    def mean(implicit N: Numeric[A]): Double =
      N.toDouble(a.sum) / a.size

    def sse(implicit N: Numeric[A]): Double = {
      val m = a.mean
      a.foldMap(x => math.pow(N.toDouble(x) - m, 2))
    }

    def variance(implicit N: Numeric[A]) = a.sse / a.size

    def stdev(implicit N: Numeric[A]) = math.sqrt(a.variance)

    def modesNel: NonEmptyList[A] = {
      NonEmptyList.fromList(a.modes).get
    }
  }

  implicit class RichFoldable[F[_]: Foldable, A](val fa: F[A]) {

    // def counts: Map[A, Int] =
    //   fa.foldLeft(Map.empty[A, Int].withDefaultValue(0)) {
    //     case (m, a) => m.updated(a, m(a) + 1)
    //   }

    def getAtIndex(i: Int): Option[A] = fa.foldM[Either[A, ?], Int](i) {
      case (0, a) => Left(a)
      case (i, _) => Right(i - 1)
    }.swap.toOption

    def sum(implicit N: Numeric[A]): A = fa.foldLeft(N.fromInt(0))(N.plus)

    def product(implicit N: Numeric[A]): A = fa.foldLeft(N.fromInt(0))(N.times)

    def meanOpt(implicit N: Numeric[A]): Option[Double] = {
      val (sum, count) = fa.foldLeft(N.fromInt(0), N.fromInt(0)) {
        case ((curSum, curCount), a) => (N.plus(curSum, a), N.plus(curCount, N.fromInt(1)))
      }
      if(count == 0) None else Some(N.toDouble(sum) / N.toDouble(count))
    }

    def proportionOpt(predicate: A => Boolean): Option[Double] = fa.foldLeft((0, 0)) {
      case ((trues, total), a) =>
        if(predicate(a)) (trues + 1, total + 1)
        else (trues, total + 1)
    } match { case (trues, total) => if(total == 0) None else Some(trues.toDouble / total) }

    // unsafe
    def proportion(predicate: A => Boolean): Double = fa.foldLeft((0, 0)) {
      case ((trues, total), a) =>
        if(predicate(a)) (trues + 1, total + 1)
        else (trues, total + 1)
    } match { case (trues, total) => trues.toDouble / total }

    def headOption: Option[A] = fa.foldLeft(None: Option[A]) {
      case (None, a) => Some(a)
      case (head, _) => head
    }

    def lastOption: Option[A] = fa.foldLeft(None: Option[A]) {
      case (None, a) => Some(a)
      case (_, a) => Some(a)
    }

    def modes: List[A] = {
      NonEmptyList.fromList(fa.toList.groupBy(identity).toList.sortBy(-_._2.size)).map { nel =>
        nel.filter(_._2.size == nel.head._2.size)
      }.foldK.map(_._1)
    }

    def groupSecondByFirst[B, C](implicit ev: (A =:= (B, C))): Map[B, List[C]] = {
      fa.toList.groupBy(_._1).map { case (k, ps) => k -> ps.map(_._2) }
    }

    // TODO other optional versions

    def sseOpt(implicit N: Numeric[A]): Option[Double] = {
      fa.meanOpt.map(m =>
        fa.toList.map(x => math.pow(N.toDouble(x) - m, 2)).sum
      )
    }
    def varianceOpt(implicit N: Numeric[A]) = fa.sseOpt.map(_ / fa.size)
    def varianceSampleOpt(implicit N: Numeric[A]) = fa.sseOpt.map(_ / (fa.size - 1))

    def stdevOpt(implicit N: Numeric[A]) = fa.varianceOpt.map(math.sqrt)
    def stdevSampleOpt(implicit N: Numeric[A]) = fa.varianceSampleOpt.map(math.sqrt)
  }
}
