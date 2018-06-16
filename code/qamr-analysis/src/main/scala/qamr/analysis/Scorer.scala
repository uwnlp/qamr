package qamr.analysis

import scala.collection.mutable
import scala.math.Numeric

/** Convenience class for scoring things with numbers. Mutable.
  * Not writing scaladoc because it's pretty self-explanatory...
  */
class Scorer[A, N] private (private[this] val map: mutable.Map[A, N])(implicit N: Numeric[N]) {
  @inline def apply(a: A): N = get(a)
  def get(a: A): N = map.get(a).getOrElse(N.zero)
  def increment(a: A): Unit = add(a, N.one)
  def add(a: A, n: N): Unit = map.put(a, N.plus(get(a), n))

  def addAll(other: Iterator[(A, N)]): Unit =
    other.foreach(Function.tupled(add))

  def iterator: Iterator[(A, N)] = map.iterator
  def keyIterator: Iterator[A] = map.keys.iterator

  def isEmpty: Boolean = map.isEmpty
  def nonEmpty: Boolean = map.nonEmpty

  def size: Int = map.size
  def max: N = if(map.isEmpty) N.zero else map.values.max
  def min: N = if(map.isEmpty) N.zero else map.values.min
  def sum: N = map.values.sum
  def mean: Double = N.toDouble(sum) / size.toDouble
  def median: Double = {
    if(map.isEmpty) {
      0.0
    } else {
      val sorted = iterator.map(_._2).toVector.sorted
      if(sorted.size % 2 == 0) {
        N.toDouble(N.plus(sorted(sorted.size / 2 - 1), sorted(sorted.size / 2))) / 2.0
      } else {
        N.toDouble(sorted(sorted.size / 2))
      }
    }
  }

  def sizeIf(p: A => Boolean): Int = map.keys.filter(p).size
  def sumIf(p: A => Boolean): N = map.filter(x => p(x._1)).map(_._2).sum
}

object Scorer {
  def apply[A, N : Numeric](): Scorer[A, N] = {
    new Scorer(mutable.Map.empty[A, N])
  }

  def apply[A, N : Numeric](items: TraversableOnce[A]): Scorer[A, N] = {
    val map = mutable.Map.empty[A, N]
    val counter = new Scorer(map)
    items.foreach(i => counter.increment(i))
    counter
  }
}
