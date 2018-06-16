package qamr.analysis

import qamr.QAData
import qamr.SourcedQA

import qamr.example.AnnotationConfig

import nlpdata.datasets.wiktionary.Inflections

import nlpdata.util.HasTokens
import nlpdata.util.HasTokens.ops._
import nlpdata.util.LowerCaseStrings._
import nlpdata.util.Text

class ExternalPhrases[SID](
  annotationConfig: AnnotationConfig,
  data: QAData[SID])(
  implicit sidHasTokens: HasTokens[SID],
  inflections: Inflections
) {

  def getAlignedQuestionIndices(
    sentence: Vector[String],
    questionTokens: Vector[String])(
    implicit inflections: Inflections
  ): Set[Int] = {
    val lowerSentence = sentence.map(_.lowerCase)
    val allIndices = for {
      (t, index) <- questionTokens.zipWithIndex
      if !annotationConfig.isReallyUninteresting(t)
      lowerToken = Text.normalizeToken(t).lowerCase
      tokenForm <- t.lowerCase :: inflections.getAllForms(lowerToken).toList
      if lowerSentence.contains(tokenForm)
    } yield index
    allIndices.toSet
  }

  val whDetWords = Set("what", "how", "which").map(_.lowerCase)
  case class PhraseState(curPhrase: List[LowerCaseString], phrases: List[List[LowerCaseString]]) {
    def finish(include: Boolean) = PhraseState(Nil, if(include && curPhrase.nonEmpty) curPhrase :: phrases else phrases)
    def extend(token: String) = this.copy(curPhrase = token.lowerCase :: this.curPhrase)

    def hasQWord: Boolean = curPhrase.exists(whDetWords.contains)
    def hasNonQWord: Boolean = curPhrase.exists(!whDetWords.contains(_))
  }

  val interestingWords = Set("first", "last", "second", "third", "fourth")
  def isInteresting(t: String) = !annotationConfig.isReallyUninteresting(t) || interestingWords.contains(t)

  def getExternalPhrases(sqa: SourcedQA[SID]) = {
    val tokens = sqa.id.sentenceId.tokens
    val qTokens = Tokenizer.tokenize(sqa.question.toLowerCase)
    val alignedIndices = getAlignedQuestionIndices(tokens, qTokens)
    val finalPhraseState = qTokens.indices.foldRight(PhraseState(Nil, Nil)) { case (index, phraseState) =>
      val token = qTokens(index)
      if(whDetWords.contains(token.lowerCase)) {
        phraseState.extend(token)
      } else if(!alignedIndices.contains(index) && isInteresting(token) && !annotationConfig.pronouns.contains(token.lowerCase)) {
        if(phraseState.hasQWord) phraseState.finish(phraseState.hasNonQWord).extend(token)
        else phraseState.extend(token)
      } else {
        phraseState.finish(phraseState.hasNonQWord)
      }
    }
    finalPhraseState.finish(finalPhraseState.hasNonQWord).phrases
  }
  def getAllExternalPhrases(sqa: SourcedQA[SID]) = {
    getExternalPhrases(sqa).map(_.mkString(" "))
  }
  def getWhExternalPhrases(sqa: SourcedQA[SID]) = {
    getExternalPhrases(sqa).filter(_.exists(t => whDetWords.contains(t))).map(_.mkString(" "))
  }
  def getNonWhExternalPhrases(sqa: SourcedQA[SID]) = {
    getExternalPhrases(sqa).filter(!_.exists(t => whDetWords.contains(t))).map(_.mkString(" "))
  }

  class Analysis(extractRelationWords: SourcedQA[SID] => List[String]) {
    lazy val relationWordCounts = Scorer[String, Int](
      data.all.iterator.flatMap(extractRelationWords)
    )
    lazy val questionsWithRelationWords = data.filterByQA(sqa => extractRelationWords(sqa).nonEmpty)

    lazy val questionsByRelation = {
      var res = Map.empty[String, List[SourcedQA[SID]]]
      questionsWithRelationWords.all.iterator.foreach { sqa =>
        extractRelationWords(sqa).foreach { w =>
          res = res.updated(w, sqa :: res.get(w).getOrElse(Nil))
        }
      }
      res
    }

    lazy val totalNumRelationWords = relationWordCounts.size
    lazy val totalCountRelationWords = relationWordCounts.sum

    lazy val orderedCountedRelationWords = relationWordCounts.iterator.toVector.sortBy(-_._2)
    lazy val relationWordPrintables = orderedCountedRelationWords.map(p => s"${p._1} ${p._2} (${p._2 * 100.0 / totalCountRelationWords})")

    // lazy val qaSample = sampleQAs(shuffleRand, )
    def computeCoverageAtPercentile(topPercentile: Double) = {
      val numPhrasesIncluded = math.round(totalNumRelationWords * topPercentile).toInt
      val countCovered = orderedCountedRelationWords.take(numPhrasesIncluded).map(_._2).sum
      s"${topPercentile * 100.0}% ($numPhrasesIncluded): ${pctString(countCovered, totalCountRelationWords)}"
    }

    lazy val report = s"""
      |Number of questions with relation phrase: ${pctString(questionsWithRelationWords.all.size, data.all.size)}
      |Number of relation phrases: $totalNumRelationWords
      |Number of relation phrase instances: $totalCountRelationWords
      |Relation phrase question coverages at percentiles:
      |${computeCoverageAtPercentile(0.001)}
      |${computeCoverageAtPercentile(0.01)}
      |${computeCoverageAtPercentile(0.05)}
      |${computeCoverageAtPercentile(0.10)}
      |${computeCoverageAtPercentile(0.25)}
      |${computeCoverageAtPercentile(0.50)}
      |${computeCoverageAtPercentile(0.90)}

      |Sample phrases:
      |${relationWordPrintables.take(20).mkString("\n")}
      |""".stripMargin.trim

  }

  val allRelationWordAnalysis   = new Analysis(getAllExternalPhrases)
  val whRelationWordAnalysis    = new Analysis(getWhExternalPhrases)
  val nonWhRelationWordAnalysis = new Analysis(getNonWhExternalPhrases)

  val fullReport = List(
    allRelationWordAnalysis,
    whRelationWordAnalysis,
    nonWhRelationWordAnalysis
  ).map(_.report).mkString("\n\n")
}

object ExternalPhrases {

  // We require questions to begin with one of these words.
  // val whWords = Set("who", "what", "when", "where", "why", "how", "which", "whose").map(_.lowerCase)

  // def beginsWithWhSpace(s: String): Boolean = whWords.exists(w => s.lowerCase.startsWith(w + " ".lowerCase))

  // def getWordsInQuestion(
  //   sentence: Vector[String],
  //   string: String)(
  //   implicit inflections: Inflections
  // ): Set[Int] = {
  //   val tokens = tokenize(string).filterNot(isReallyUninteresting)
  //   val moreTokens = tokens.map(t => Text.normalizeToken(t).lowerCase).flatMap(inflections.getAllForms)
  //   val generalizedTokens = tokens.map(_.lowerCase) ++ moreTokens
  //   sentence.zipWithIndex.filter(p => generalizedTokens.contains(p._1.lowerCase)).map(_._2).toSet
  // }


  // def getQuestionSentenceAlignments(
  //   sentence: Vector[String],
  //   questionTokens: Vector[String])(
  //   implicit inflections: Inflections
  // ): Set[(Int, Int)] = {
  //   val lowerSentence = sentence.map(_.lowerCase)
  //   val lowerQuestion = questionTokens.map(_.lowerCase)
  //   val allIndices = for {
  //     (qToken, qIndex) <- lowerQuestion.zipWithIndex
  //     if !isReallyUninteresting(qToken)
  //     lowerQToken = Text.normalizeToken(qToken).lowerCase
  //     qTokenForm <- qToken :: inflections.getAllForms(lowerQToken).toList
  //     (sToken, sIndex) <- lowerSentence.zipWithIndex
  //     lowerSToken = Text.normalizeToken(sToken).lowerCase
  //     sTokenForm <- sToken :: inflections.getAllForms(lowerSToken).toList
  //     if qTokenForm.equals(sTokenForm)
  //   } yield (qIndex, sIndex)
  //   allIndices.toSet
  // }
}
