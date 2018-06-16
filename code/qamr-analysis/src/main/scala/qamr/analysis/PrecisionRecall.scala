package qamr.analysis

import cats.Monoid

case class PrecisionRecall(
  numPredicted: Double,
  numGold: Double,
  numCorrect: Double,
  numCovered: Double) {
  val precision = numCorrect / numPredicted
  val recall = numCovered / numGold
  val f1 = 2 * precision * recall / (precision + recall)

  def statString = f"F1: $f1%.3f\tPrecision: $precision%.3f\tRecall: $recall%.3f"

  def aggregate(other: PrecisionRecall) = PrecisionRecall(
    numPredicted + other.numPredicted,
    numGold + other.numGold,
    numCorrect + other.numCorrect,
    numCovered + other.numCovered)
}
object PrecisionRecall {
  val zero = PrecisionRecall(0, 0, 0, 0)

  val precisionRecallMonoid = new Monoid[PrecisionRecall] {
    def empty = zero
    def combine(x: PrecisionRecall, y: PrecisionRecall) = x aggregate y
  }
}
