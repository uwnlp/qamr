package qamr.example

import nlpdata.datasets.ptb.PTBPath
import nlpdata.datasets.ptb.PTBSentencePath
import nlpdata.datasets.wiki1k.Wiki1kPath
import nlpdata.datasets.wiki1k.Wiki1kSentencePath

sealed trait SentenceId {
  def getPTB = Some(this) collect { case id @ PTBSentenceId(_) => id }
  def isPTB = getPTB.nonEmpty

  def getWiki = Some(this) collect { case id @ WikiSentenceId(path) => id }
  def isWiki = getWiki.nonEmpty
}
case class PTBSentenceId(path: PTBSentencePath) extends SentenceId
case class WikiSentenceId(path: Wiki1kSentencePath) extends SentenceId

object SentenceId {

  private[this] val PTBMatch = "PTB:([^:]+):([0-9]+)".r
  private[this] val Wiki1kMatch = "Wiki1k:([^:]+):([^:]+):([0-9]+):([0-9]+)".r

  // not necessarily used for serialization over the wire, but
  // used for storing to / reading from  the dataset file.
  def toString(sid: SentenceId): String = sid match {
    case PTBSentenceId(path) => s"PTB:${path.filePath.suffix}:${path.sentenceNum}"
    case WikiSentenceId(path) => s"Wiki1k:${path.filePath.domain}:${path.filePath.suffix}:${path.paragraphNum}:${path.sentenceNum}"
  }

  def fromString(s: String): SentenceId = s match {
    case PTBMatch(suffix, sentenceNum) =>
      PTBSentenceId(PTBSentencePath(PTBPath(suffix), sentenceNum.toInt))
    case Wiki1kMatch(domain, suffix, paragraphNum, sentenceNum) =>
      WikiSentenceId(Wiki1kSentencePath(Wiki1kPath(domain, suffix), paragraphNum.toInt, sentenceNum.toInt))
  }

}
