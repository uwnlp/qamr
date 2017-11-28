package qamr

import cats.Traverse
import cats.Foldable
import cats.implicits._

import qamr.util.beginsWithWhSpace

import spacro.HITInfo

import nlpdata.util.HasTokens
import nlpdata.util.HasTokens.ops._
import nlpdata.util.Text

import scala.util.{Try, Success, Failure}

object DataFiles {

  def renderValidationAnswer(
    va: ValidationAnswer
  ): String = va match {
    case InvalidQuestion => "Invalid"
    case Redundant(i) => s"Redundant-$i"
    case Answer(span) => span.toVector.sorted.mkString(" ")
  }

  private[this] def readIntSet(s: String) = Try(s.split(" ").map(_.toInt).toSet)
  private[this] val redundantRegex = """Redundant-([0-9]+)""".r
  private[this] object IntMatch {
    def unapply(s: String): Option[Int] = scala.util.Try(s.toInt).toOption
  }

  def readValidationAnswer(s: String): Try[ValidationAnswer] = s match {
    case "Invalid" => Success(InvalidQuestion)
    case redundantRegex(IntMatch(i)) => Success(Redundant(i))
    case answerIndices => readIntSet(answerIndices).map(Answer(_))
  }

  def readValidationAnswerWithWorkerId(s: String) = Try {
    val Array(workerId, valAnswer) = s.split(":")
    readValidationAnswer(valAnswer).map(workerId -> _)
  }.flatten

  def readTSV[F[_] : Traverse, SID](
    lines: F[String],
    idFromString: String => SID
  ): Try[QAData[SID]] = {
    lines.traverse { line =>
      Try {
        val fields = line.split("\t")
        val id = idFromString(fields(0))
        val keywords = readIntSet(fields(1)).get.toList.sorted
        val generatorId = fields(2)
        val qaIndex = fields(3).toInt
        val qaPairId = QAPairId(id, keywords, generatorId, qaIndex)

        val keyword = fields(4).toInt
        val question = fields(5)
        val origAnswer = readIntSet(fields(6)).get
        val wqa = WordedQAPair(keyword, question, origAnswer)

        val valResponsesTry = List(
          readValidationAnswerWithWorkerId(fields(7)),
          readValidationAnswerWithWorkerId(fields(8))
        ).sequence

        valResponsesTry.map(SourcedQA(qaPairId, wqa, _))
      }.flatten
    }.map(sqas => QAData(sqas.toList))
  }

  def makeSentenceIndex[F[_]: Foldable, SID : HasTokens](
    ids: F[SID],
    writeId: SID => String
  ): String = {
    ids.toList.map(id => s"${writeId(id)}\t${id.tokens.mkString(" ")}").mkString("\n")
  }

  def makeFinalQAPairTSV[F[_]: Foldable, SID](
    ids: F[SID],
    writeId: SID => String, // serialize sentence ID for distribution in data file
    anonymizeWorker: String => String, // anonymize worker IDs so they can't be tied back to workers on Turk
    genInfos: List[HITInfo[GenerationPrompt[SID], List[WordedQAPair]]],
    valInfos: List[HITInfo[ValidationPrompt[SID], List[ValidationAnswer]]],
    filterBadQAs: Boolean
  ): String = {
    val genInfosBySentenceId = genInfos.groupBy(_.hit.prompt.id)
    val valInfosByGenAssignmentId = valInfos.groupBy(_.hit.prompt.sourceAssignmentId)
    val sb = new StringBuilder
    for(id <- ids.toList) {
      var shouldIncludeSentence = false
      val sentenceSB = new StringBuilder
      val idString = writeId(id)
      // sort by keyword group first...
      for(HITInfo(genHIT, genAssignments) <- genInfosBySentenceId(id).sortBy(_.hit.prompt.keywords.min)) {
        // then worker ID second, so the data will be chunked correctly according to HIT;
        for(genAssignment <- genAssignments.sortBy(_.workerId)) {
          // and these should already be ordered in terms of the target word used for a QA pair.
          for((wqa, qaIndex) <- genAssignment.response.zipWithIndex) {
            // pairs of (validation worker ID, validation answer)
            val valResponses = valInfosByGenAssignmentId.get(genAssignment.assignmentId).getOrElse(Nil)
              .flatMap(_.assignments.map(a => (a.workerId, a.response(qaIndex))))
            if(valResponses.size != 2) {
              System.err.println(
                "Warning: don't have 2 validation answers for question. Actual number: " + valResponses.size
              )
            } else if(
              !filterBadQAs || (
                valResponses.forall(_._2.isAnswer) && beginsWithWhSpace(wqa.question))
            ) {
              shouldIncludeSentence = true
              sentenceSB.append(idString + "\t") // 0: string representation of sentence ID
              sentenceSB.append(genHIT.prompt.keywords.toList.sorted.mkString(" ") + "\t") // 1: space-separated set of keywords presented to turker
              sentenceSB.append(anonymizeWorker(genAssignment.workerId) + "\t") // 2: anonymized worker ID
              sentenceSB.append(qaIndex + "\t") // 3: index of the QA in the generation HIT
              sentenceSB.append(wqa.wordIndex + "\t") // 4: index of keyword in sentence
              sentenceSB.append(wqa.question + "\t") // 5: question string written by worker
              sentenceSB.append(wqa.answer.toVector.sorted.mkString(" ") + "\t") // 6: answer indices given by original worker
              sentenceSB.append(
                valResponses.map { case (valWorkerId, valAnswer) =>
                  anonymizeWorker(valWorkerId) + ":" + renderValidationAnswer(valAnswer) // 7-8: validator responses
                }.mkString("\t")
              )
              sentenceSB.append("\n")
            }
          }
        }
      }
      if(shouldIncludeSentence) {
        sb.append(sentenceSB.toString)
      }
    }
    sb.toString
  }

  def makeFinalReadableQAPairTSV[F[_]: Foldable, SID : HasTokens](
    ids: F[SID],
    writeId: SID => String, // serialize sentence ID for distribution in data file
    anonymizeWorker: String => String, // anonymize worker IDs so they can't be tied back to workers on Turk
    genInfos: List[HITInfo[GenerationPrompt[SID], List[WordedQAPair]]],
    valInfos: List[HITInfo[ValidationPrompt[SID], List[ValidationAnswer]]],
    filterBadQAs: Boolean
  ): String = {
    val genInfosBySentenceId = genInfos.groupBy(_.hit.prompt.id)
    val valInfosByGenAssignmentId = valInfos.groupBy(_.hit.prompt.sourceAssignmentId)
    val sb = new StringBuilder
    for(id <- ids.toList) {
      var shouldIncludeSentence = false
      val sentenceSB = new StringBuilder
      val idString = writeId(id)
      sentenceSB.append("#" + idString + "\n")
      sentenceSB.append(Text.render(id) + "\n")
      // sort by keyword group first...
      for(HITInfo(genHIT, genAssignments) <- genInfosBySentenceId(id).sortBy(_.hit.prompt.keywords.min)) {
        // then worker ID second, so the data will be chunked correctly according to HIT;
        for(genAssignment <- genAssignments.sortBy(_.workerId)) {
          // and these should already be ordered in terms of the target word used for a QA pair.
          for((wqa, qaIndex) <- genAssignment.response.zipWithIndex) {
            // pairs of (validation worker ID, validation answer)
            val valResponses = valInfosByGenAssignmentId.get(genAssignment.assignmentId).getOrElse(Nil)
              .flatMap(_.assignments.map(a => (a.workerId, a.response(qaIndex))))
            if(valResponses.size != 2) {
              System.err.println(
                "Warning: don't have 2 validation answers for question. Actual number: " + valResponses.size
              )
            } else if(beginsWithWhSpace(wqa.question)) {
              valResponses.map(_._2.getAnswer).sequence.foreach { valAnswers =>
                val answersString = valAnswers.map(a => Text.renderSpan(id, a.indices)).mkString("\t")
                shouldIncludeSentence = true
                sentenceSB.append(f"${wqa.question}%-50s")
                sentenceSB.append(answersString)
                sentenceSB.append("\n")
              }
            }
          }
        }
      }
      if(shouldIncludeSentence) {
        sb.append(sentenceSB.toString + "\n")
      }
    }
    sb.toString
  }
}
