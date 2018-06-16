package qamr

import qamr.util._

import cats.Monoid

/** Holds a subset of the data gathered in the turk task,
  * for the purposes of analysis. Has convenience functions for filtering as well.
  */
class QAData[SID](
  val all: List[SourcedQA[SID]],
  val idToQA: Map[QAPairId[SID], SourcedQA[SID]],
  val sentenceToQAs: Map[SID, List[SourcedQA[SID]]]
) {

  def this(_all: List[SourcedQA[SID]]) = this(
    _all,
    _all.map(sqa => sqa.id -> sqa).toMap,
    _all.groupBy(_.id.sentenceId))

  def filterBySentence(p: SID => Boolean) = new QAData(
    all.filter(sqa => p(sqa.id.sentenceId)),
    idToQA.filter(x => p(x._1.sentenceId)),
    sentenceToQAs.filter(x => p(x._1)))

  def filterByQA(p: SourcedQA[SID] => Boolean) = new QAData(
    all.filter(p),
    idToQA.filter(x => p(x._2)),
    sentenceToQAs.flatMap { case (id, qas) =>
      val newQAs = qas.filter(p)
      Some(id -> newQAs).filter(const(newQAs.nonEmpty))
    })

  def combine(that: QAData[SID]): QAData[SID] = QAData(this.all ++ that.all)
}

object QAData {
  def apply[SID](sqas: List[SourcedQA[SID]]): QAData[SID] = {
    val idToQA = sqas.map(sqa => sqa.id -> sqa).toMap
    val sentenceToQAs = sqas.groupBy(_.id.sentenceId)
    new QAData(sqas, idToQA, sentenceToQAs)
  }

  implicit def qaDataMonoid[SID] = new Monoid[QAData[SID]] {
    def empty: QAData[SID] = QAData[SID](Nil)
    def combine(x: QAData[SID], y: QAData[SID]): QAData[SID] = x combine y
  }
}
