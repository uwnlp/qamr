package qamr.analysis

import qamr.QAData
import qamr.SourcedQA
import qamr.Answer
import qamr.InvalidQuestion
import qamr.Redundant

import cats.data.NonEmptyList
import cats.implicits._

import nlpdata.util.HasTokens
import nlpdata.util.HasTokens.ops._

class Validation[SID](
  data: QAData[SID])(
  implicit sidHasTokens: HasTokens[SID]
) {

  import qamr.QAMRSettings._

  def isGood(sqa: SourcedQA[SID]) = sqa.isValid && qamr.util.beginsWithWhSpace(sqa.question)

  case class KeywordInfo(sqas: NonEmptyList[SourcedQA[SID]]) {
    def keywords = sqas.head.id.keywords
    def numQAs = sqas.size
    def numValidQAs = sqas.filter(_.isValid).size
    def numGoodQAs = sqas.filter(isGood).size
  }

  case class AssignmentInfo(keywordInfos: NonEmptyList[KeywordInfo]) {
    def sqas = keywordInfos.flatMap(_.sqas)
    def keywords = keywordInfos.head.keywords
    def numQAs = sqas.size
    def numValidForBonus = math.round(
      sqas.map(_.validatorAnswers.filter(_.isAnswer).size).mean - 0.01
    ).toInt
    def numValidQAs = sqas.filter(_.isValid).size
    def numGoodQAs = sqas.filter(isGood).size
    def genReward = generationReward + (1 to (numValidQAs - keywords.size)).map(bonusFor).sum
    def valReward = validationReward + (validationBonusPerQuestion * math.max(0, numQAs - 4))
    def totalReward = genReward + valReward
    def numKeywords = keywordInfos.size
  }

  case class SentenceInfo(assignmentInfos: NonEmptyList[AssignmentInfo]) {
    def sqas = assignmentInfos.flatMap(_.sqas)
    def id = sqas.head.id.sentenceId
    def numQAs = sqas.size
    def numValidQAs = sqas.filter(_.isValid).size
    def numGoodQAs = sqas.filter(isGood).size
    def cost = assignmentInfos.map(_.totalReward.toDouble).sum
    def costPerToken = cost / id.tokens.size
    def numKeywords = assignmentInfos.map(_.numKeywords).sum
  }

  lazy val sentenceInfos = NonEmptyList.fromList(
    data.sentenceToQAs.values.iterator.map { sentenceSQAs =>
      SentenceInfo(
        NonEmptyList.fromList(
          sentenceSQAs.toList.groupBy(sqa =>
            (sqa.id.sentenceId, sqa.id.keywords, sqa.id.workerId)
          ).values.map { assignmentSQAs =>
            AssignmentInfo(
              NonEmptyList.fromList(
                assignmentSQAs.groupBy(_.wqa.wordIndex).values.map { sqas =>
                  KeywordInfo(NonEmptyList.fromList(sqas).get)
                }.toList
              ).get
            )
          }.toList
        ).get
      )
    }.toList
  ).get

  lazy val assignmentInfos = sentenceInfos.flatMap(_.assignmentInfos)
  lazy val keywordInfos = assignmentInfos.flatMap(_.keywordInfos)

  lazy val numSentences = sentenceInfos.size
  lazy val numKeywords = sentenceInfos.map(_.numKeywords).sum

  sealed trait AgreementClass
  case object BothInvalid extends AgreementClass
  case object OneInvalid extends AgreementClass
  case object BothRedundant extends AgreementClass
  case object OneRedundant extends AgreementClass
  case object BothWithOriginal extends AgreementClass
  case object BothButNotOriginal extends AgreementClass
  case object OneWithOriginal extends AgreementClass
  case object NoIntersection extends AgreementClass
  lazy val agreementClasses = data.all.map(sqa => (sqa, sqa.validatorAnswers)).collect {
    case (sqa, List(InvalidQuestion, InvalidQuestion)) => BothInvalid
    case (sqa, List(InvalidQuestion, _)) => OneInvalid
    case (sqa, List(_, InvalidQuestion)) => OneInvalid
    case (sqa, List(Redundant(_), Redundant(_))) => BothRedundant
    case (sqa, List(_, Redundant(_))) => OneRedundant
    case (sqa, List(Redundant(_), _)) => OneRedundant
    case (sqa, List(Answer(a1), Answer(a2))) if (sqa.wqa.answer :: a1 :: a2 :: Nil).reduce(_ intersect _).nonEmpty => BothWithOriginal
    case (sqa, List(Answer(a1), Answer(a2))) if (a1 :: a2 :: Nil).reduce(_ intersect _).nonEmpty => BothButNotOriginal
    case (sqa, (List(Answer(a1), Answer(a2)))) if (
      (a1 intersect sqa.wqa.answer).nonEmpty || (a2 intersect sqa.wqa.answer).nonEmpty
    ) => OneWithOriginal
    case (sqa, List(Answer(a1), Answer(a2))) if (
      (sqa.wqa.answer ++ a1 ++ a2).size == (sqa.wqa.answer.size + a1.size + a2.size)
    ) => NoIntersection
  }

  lazy val agClassCounts = Scorer[AgreementClass, Int](agreementClasses.iterator)

  lazy val agClassHist: String = {
    val keys = Vector(
      BothInvalid, OneInvalid, BothRedundant, OneRedundant,
      BothWithOriginal, BothButNotOriginal, OneWithOriginal, NoIntersection
    )
    val total = agClassCounts.sum.toDouble
    def pct(n: Int) = n / total * 100.0
    val max = agClassCounts.max
    val scaleMax = 50.0
    val scaleFactor = scaleMax / max
    def scale(n: Int): Int = math.ceil(n.toDouble * scaleFactor).toInt
    def pounds(n: Int) = "#" * n
    keys.zip(keys.map(agClassCounts.apply))
      .map { case (c, n) => f"$c%18s |${pounds(scale(n))}%s $n%d (${pct(n)}%.2f)"}
      .mkString("\n")
  }

  lazy val collapsedAgClassCounts = Scorer[String, Int](
    agreementClasses.iterator.map {
      case BothInvalid => "at least one invalid"
      case OneInvalid => "at least one invalid"
      case BothRedundant => "at least one redundant"
      case OneRedundant => "at least one redundant"
      case BothWithOriginal => "all agree"
      case BothButNotOriginal => "two agree"
      case OneWithOriginal => "two agree"
      case NoIntersection => "none agree"
    }
  )

  lazy val collapsedAgClassHist: String = {
    val keys = Vector(
      "all agree", "two agree", "none agree",
      "at least one invalid", "at least one redundant"
    )
    val total = collapsedAgClassCounts.sum.toDouble
    def pct(n: Int) = n / total * 100.0
    val max = collapsedAgClassCounts.max
    val scaleMax = 50.0
    val scaleFactor = scaleMax / max
    def scale(n: Int): Int = math.ceil(n.toDouble * scaleFactor).toInt
    def pounds(n: Int) = "#" * n
    keys.zip(keys.map(collapsedAgClassCounts.apply))
      .map { case (c, n) => f"$c%22s |${pounds(scale(n))}%s $n%d (${pct(n)}%.2f)"}
      .mkString("\n")
  }


  lazy val report = f"""
Aggregate stats:
Number of questions: ${sentenceInfos.map(_.numQAs).sum}%s
Number of valid questions: ${pctString(sentenceInfos.map(_.numValidQAs).sum, sentenceInfos.map(_.numQAs).sum.toInt)}%s
Number of good questions: ${pctString(sentenceInfos.map(_.numGoodQAs).sum, sentenceInfos.map(_.numValidQAs).sum)}%s

Validator agreement class counts:
$agClassHist

Validator agreement class counts (collapsed):
$collapsedAgClassHist

Sentences:
Number of sentences: $numSentences%s
Number of sentences with good QAs: ${pctString(data.sentenceToQAs.size, sentenceInfos.size.toInt)}%s
Number of keywords: $numKeywords%s (${numKeywords.toDouble / numSentences}%.2f per sentence)
Sentence costs: ${noSumDistString(sentenceInfos.map(_.cost))}%s
Sentence cost per token: ${noSumDistString(sentenceInfos.map(_.costPerToken))}%s
Number of questions (per sentence): ${noSumDistString(sentenceInfos.map(_.numQAs))}%s
Number of valid questions (per sentence): ${noSumDistString(sentenceInfos.map(_.numValidQAs))}%s
Number of good questions (per sentence): ${noSumDistString(sentenceInfos.map(_.numGoodQAs))}%s

Assignments:
Number of assignments: ${assignmentInfos.size}%s
Assignment costs: ${noSumDistString(assignmentInfos.map(_.totalReward))}%s
Number of questions (per assignment): ${noSumDistString(assignmentInfos.map(_.numQAs))}%s
Number of valid questions (per assignment): ${noSumDistString(assignmentInfos.map(_.numValidQAs))}%s
Number of good questions (per assignment): ${noSumDistString(assignmentInfos.map(_.numGoodQAs))}%s

Keywords:
Number of keywords: ${keywordInfos.size}%s
Number of questions per keyword: ${noSumDistString(keywordInfos.map(_.numQAs))}%s
Number of valid questions per keyword: ${noSumDistString(keywordInfos.map(_.numValidQAs))}%s
Number of good questions per keyword: ${noSumDistString(keywordInfos.map(_.numGoodQAs))}%s
Number of keywords missing: ${pctString(keywordInfos.filter(_.numValidQAs == 0).size, keywordInfos.size.toInt)}%s
""".trim
}
