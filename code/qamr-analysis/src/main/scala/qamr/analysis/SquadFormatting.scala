package qamr.analysis

import qamr.QAData
import qamr.example.SentenceId
import qamr.example.WikiSentenceId

// import cats._
import cats.data.State
import cats.implicits._

import nlpdata.util.Text
import nlpdata.util.HasTokens
import nlpdata.util.HasTokens.ops._

import monocle.macros._

class SquadFormatting(implicit sentenceIdHasTokens: HasTokens[SentenceId]) {

  def getOffsetAndSpan(reference: List[String], span: Set[Int]) = {

    if(span.isEmpty) {
      System.err.println("Identifying offset of empty span for reference:\n" + Text.render(reference))
    }
    if(span.exists(i => i < 0 || i >= reference.size)) {
      System.err.println("Identifying offset of span containing indices outside of reference:\n" +
                           Text.render(reference) + "\n" +
                           span.mkString(" "))
    }

    @Lenses case class OffsetState(curOffset: Int, inSpan: Boolean, beginOffset: Int, phrase: String)
    type ST[A] = State[OffsetState, A]
    val firstWord = span.min
    val lastWord = span.max
    def possiblyAddToPhrase(text: String) =
      State.modify(OffsetState.curOffset.modify(_ + text.length)) >>
        State.modify(s =>
          if(s.inSpan) OffsetState.phrase.modify(_ + text)(s) else s
        )
    def emitToken(token: String, index: Int): ST[String] = {
      val normalizedToken = Text.normalizeToken(token)
      for {
        _ <- State.modify(if(index == firstWord) OffsetState.inSpan.set(true) else identity[OffsetState])
        _ <- State.modify(if(index == firstWord) (s: OffsetState) => OffsetState.beginOffset.set(s.curOffset)(s)
                          else identity[OffsetState])
        _ <- possiblyAddToPhrase(normalizedToken)
        _ <- State.modify(if(index == lastWord) OffsetState.inSpan.set(false) else identity[OffsetState])
      } yield normalizedToken
    }

    val OffsetState(_, _, begin, phrase) = Text.renderM[(String, Int), List, ST, String](
      reference.zipWithIndex,
      _._1,
      _ => emitToken(" ", -1),
      Function.tupled(emitToken)
    ).runS(OffsetState(0, false, -1, "")).value

    val sentence = Text.render(reference)
    val reproPhrase = sentence.substring(begin, math.min(begin + phrase.length, sentence.length))
    if(reproPhrase != phrase) {
      System.err.println(
        s"Problem for sentence\n$sentence \nGiven answer:\n$phrase \nRepro answer:\n$reproPhrase")
    }

    (begin, phrase)
  }


  def squadFormattedString(
    data: QAData[SentenceId],
    excludedTitles: Set[String]
  ): String = {
    import argonaut._
    import Argonaut._
    val idsByFile = data.sentenceToQAs.keys.collect {
      case id @ WikiSentenceId(wikiPath) => id
    }.groupBy(_.path.filePath).filter { case (filePath, _) =>
        val title = Wiki1k.getFile(filePath).get.title
        if(!excludedTitles.contains(title)) {
          true
        } else {
          System.out.println(s"Excluding file with title: $title")
          false
        }
    }

    def getAnswerSpanJson(tokens: Vector[String], answer: Set[Int]) = {
      val filledOutAnswer = (answer.min to answer.max).toSet
      val renderedSentence = Text.render(tokens)
      val (answerStart, answerText) = getOffsetAndSpan(tokens.toList, filledOutAnswer)
      Json.obj(
        "answer_start" -> jNumber(answerStart),
        "text" -> jString(answerText)
      )
    }

    def getQAJson(sentenceId: WikiSentenceId, sentenceTokens: Vector[String], qIndex: Int, question: String, answers: List[Set[Int]]) = {
      Json.obj(
        "answers" -> Json.array(answers.map(a => getAnswerSpanJson(sentenceTokens, a)): _*),
        "question" -> jString(question),
        "id" -> jString(s"${SentenceId.toString(sentenceId)}::$qIndex")
      )
    }

    def getSentenceJson(sentenceId: WikiSentenceId) = {
      val sentenceTokens = sentenceId.tokens
      val qas = data.sentenceToQAs(sentenceId).zipWithIndex.map {
        case (sqa, qIndex) => getQAJson(sentenceId, sentenceTokens, qIndex, sqa.question, sqa.answers)
      }.toSeq

      Json.obj(
        "context" -> jString(Text.render(sentenceTokens)),
        "qas" -> Json.array(qas: _*)
      )
    }

    val files: Seq[Json] = idsByFile.keys.toSeq.map { filePath =>
      val wikiFile = Wiki1k.getFile(filePath).get
      val title = wikiFile.title
      val sentenceIds = idsByFile(filePath)
      val sentenceJsons = sentenceIds.map(getSentenceJson)
      Json.obj(
        "title" -> jString(title),
        "paragraphs" -> Json.array(sentenceJsons.toSeq: _*)
      )
    }

    val result = Json.obj(
      "data" -> Json.array(files: _*),
      "version" -> jString("1.1")
    )

    result.nospaces
  }

}
