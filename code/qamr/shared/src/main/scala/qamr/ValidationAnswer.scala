package qamr

import cats.implicits._

import nlpdata.util.Text
import qamr.util._

/** Represents a validator response about a question:
  * either it has an answer, is invalid, or is redundant with another question.
  */
sealed trait ValidationAnswer {
  def isInvalid = this match {
    case InvalidQuestion => true
    case _ => false
  }

  def getRedundant = this match {
    case r @ Redundant(_) => Some(r)
    case _ => None
  }
  def isRedundant = !getRedundant.isEmpty

  def getAnswer = this match {
    case a @ Answer(_) => Some(a)
    case _ => None
  }
  def isAnswer = !getAnswer.isEmpty

  def isComplete = this match {
    case InvalidQuestion => true
    case Redundant(_) => true
    case Answer(indices) => !indices.isEmpty
  }
}
case object InvalidQuestion extends ValidationAnswer
case class Redundant(other: Int) extends ValidationAnswer
case class Answer(indices: Set[Int]) extends ValidationAnswer

object ValidationAnswer {
  def resolveRedundancy(va: ValidationAnswer, answers: List[ValidationAnswer]) =
    va.getRedundant.fold(va)(r => answers(r.other))

  def numAgreed(
    one: List[ValidationAnswer],
    two: List[ValidationAnswer]
  ) = {
    one.map(resolveRedundancy(_, one)).zip(
      two.map(resolveRedundancy(_, two))).filter {
      case (InvalidQuestion, InvalidQuestion) => true
      case (Answer(span1), Answer(span2)) => !span1.intersect(span2).isEmpty
      case _ => false
    }.size
  }

  def numValidQuestions(responses: List[List[ValidationAnswer]]) =
    math.round(responses.map(_.filter(_.isAnswer).size).meanOpt.get - 0.01).toInt

  // render a validation answer for the purpose of writing to a file
  // (just writes the highlighted indices of the answer; not readable)
  def renderIndices(
    va: ValidationAnswer
  ): String = va match {
    case InvalidQuestion => "Invalid"
    case Redundant(i) => s"Redundant: $i"
    case Answer(span) => span.toVector.sorted.mkString(" ")
  }

  val RedundantMatch = "Redundant: ([0-9]*)".r

  // inverse of ValidationAnswer.renderIndices
  def readIndices(
    s: String
  ): ValidationAnswer = s match {
    case "Invalid" => InvalidQuestion
    case RedundantMatch(i) => Redundant(i.toInt)
    case other => Answer(other.split(" ").map(_.toInt).toSet) // assume otherwise it's an answer
  }

  // render a validation response in a readable way for browsing
  def render(
    sentence: Vector[String],
    va: ValidationAnswer,
    referenceQAs: List[WordedQAPair]
  ): String = va match {
    case InvalidQuestion => "<Invalid>"
    case Redundant(i) => s"<Redundant with ${referenceQAs(i).question}>"
    case Answer(span) => Text.renderSpan(sentence, span)
  }
}
