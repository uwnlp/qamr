package qamr

import cats.implicits._

import qamr.util._

import spacro.tasks._
import spacro.ui._

import nlpdata.util.Text

import scalajs.js
import org.scalajs.dom
import org.scalajs.dom.raw._
import org.scalajs.dom.ext.KeyCode
import org.scalajs.jquery.jQuery

import scala.concurrent.ExecutionContext.Implicits.global

import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react._

import scalacss.DevDefaults._
import scalacss.ScalaCssReact._

import upickle.default._

import monocle._
import monocle.macros._
import japgolly.scalajs.react.MonocleReact._

class DashboardClient[SID : Reader : Writer] extends TaskClient[Unit, Unit, Service.UnitRequest] {

  import QAMRSettings._

  def main(): Unit = jQuery { () =>
    Styles.addToDocument()
    FullUI().renderIntoDOM(dom.document.getElementById(FieldLabels.rootClientDivLabel))
  }

  val WebsocketComponent = new WebsocketComponent[Unit, SummaryInfo[SID]]
  import WebsocketComponent._

  @Lenses case class State(
    summaryOpt: Option[SummaryInfo[SID]]
  )
  object State {
    def initial = State(None)
  }

  def percent(n: Int, total: Int) = f"$n%d (${n.toDouble * 100 / total}%.1f%%)"

  class FullUIBackend(scope: BackendScope[Unit, State]) {

    def render(state: State) = {
      Websocket(
        WebsocketProps(
          websocketURI = websocketUri,
          onMessage = (si: SummaryInfo[SID]) => scope.modState(State.summaryOpt.set(Some(si))),
          render = {
            case Connecting => <.div("Connecting to server...")
            case Connected(_) => state.summaryOpt.fold(<.div("Waiting for data...")) {
              case summary @ SummaryInfo(
                numGenActive, genWorkerStats, genFeedback,
                numValPromptsWaiting, numValActive, valWorkerInfo, valFeedback,
                lastFewSentences, aggSentenceStats) =>

                val estSentenceCompletionRate =
                  if(lastFewSentences.isEmpty) None
                  else Some {
                    val deltaHours = (aggSentenceStats.latestTime - aggSentenceStats.earliestTime) / 1000L / 60.0 / 60.0
                    lastFewSentences.size.toDouble / deltaHours
                  }

                <.div(
                  <.h2("Sentences"),
                  <.p("(There's a bug with computing the overall stats in this section... don't necessarily trust them. The other sections are fine though.)"),
                  estSentenceCompletionRate.map(r => f"Est. completion rate (sentences/hour): $r%.2f").whenDefined,
                  aggSentenceStats match {
                    case AggregateSentenceStats(
                      earliestTime, latestTime,
                      numSentences, numKeywords, numQAPairs, numValidQAPairs,
                      keywordPromptQAPairHist, keywordActualQAPairHist, validationLatencyHist,
                      generationCost, validationCost) =>

                      <.div(
                        <.div(s"Number of sentences completed: $numSentences"),
                        <.div(s"Number of keywords covered: $numKeywords"),
                        <.div(f"Number of QA pairs submitted: $numQAPairs%d (${numQAPairs.toDouble / numSentences}%.1f per sentence)"),
                        <.div(s"Number of QA pairs valid: ${percent(numValidQAPairs, numQAPairs)}"),
                        (for {
                           mean <- keywordPromptQAPairHist.mean
                           stdev <- keywordPromptQAPairHist.stdev
                         } yield <.div(f"QA pairs given per keyword prompt: $mean%.2f, stdev $stdev%.2f")).whenDefined,
                        (for {
                           mean <- keywordActualQAPairHist.mean
                           stdev <- keywordActualQAPairHist.stdev
                         } yield <.div(f"QA pairs expected to contain a keyword: $mean%.2f, stdev $stdev%.2f")).whenDefined,
                        (for {
                           mean <- validationLatencyHist.mean
                           stdev <- validationLatencyHist.stdev
                         } yield <.div(f"Latency from generation to validation (seconds): $mean%.2f, stdev $stdev%.2f")).whenDefined,
                        <.div(s"Total cost of generation: $generationCost"),
                        <.div(s"Total cost of validation: $validationCost"),
                        <.div(f"Average cost per sentence: ${(generationCost + validationCost) / numSentences}%.2f")
                      )
                  },
                  <.h2("Generation"),
                  <.div(s"Number of HITs active: $numGenActive"),
                  <.div(
                    s"Recent feedback: ",
                    <.ul(
                      genFeedback.map(a =>
                        <.li(
                          ^.onClick --> Callback(println(write(a))),
                          s"${a.workerId}: ${a.feedback}"
                        )).toVdomArray)
                  ),
                  <.h3("Generation worker stats"),
                  <.table(
                    ^.borderSpacing := "8px",
                    <.thead(
                      <.tr(
                        List("Worker ID", "Assignments", "Accuracy",
                             "Earnings", "Time spent (min)", "$ / hr", "sec / QA pair", "QA pairs", "Valid QA pairs",
                             "Warning", "Block").map(<.th(_)).toVdomArray
                      )
                    ),
                    <.tbody(
                      genWorkerStats.values.toVector.sortBy(-_.numAssignmentsCompleted).map {
                        case ws @ WorkerStats(
                          workerId, numAssignmentsCompleted,
                          numQAPairsWritten, numQAPairsValid,
                          timeSpent, earnings, warnedAt, blockedAt) =>

                          val minutesSpent = timeSpent.toDouble / 1000.0 / 60.0
                          val dollarsPerHour = earnings * 60.0 / minutesSpent
                          val sPerQA = timeSpent.toDouble / 1000.0 / numQAPairsWritten

                          <.tr(
                            List(workerId, numAssignmentsCompleted.toString, f"${ws.accuracy}%.3f",
                                 f"$earnings%.2f", f"$minutesSpent%.2f", f"$dollarsPerHour%.2f", f"$sPerQA%.2f",
                                 numQAPairsWritten.toString, numQAPairsValid.toString,
                                 warnedAt.fold("")(_.toString), blockedAt.fold("")(_.toString)
                            ).map(<.td(_)).toVdomArray
                          )
                      }.toVdomArray
                    )
                  ),
                  <.h2("Validation"),
                  <.div(s"Number of HITs active: $numValActive"),
                  <.div(s"Number of HITs queued: $numValPromptsWaiting"),
                  <.div(
                    s"Recent feedback: ",
                    <.ul(
                      valFeedback.map(a =>
                        <.li(
                          ^.onClick --> Callback(println(write(a))),
                          s"${a.workerId}: ${a.feedback}"
                        )).toVdomArray)
                  ),
                  <.h3("Validation worker stats"),
                  <.table(
                    ^.borderSpacing := "8px",
                    <.thead(
                      <.tr(
                        List("Worker ID", "Assignments", "Earnings",
                             "Time spent (min)", "$ / hr", "sec / QA pair",
                             "Agreement rate", "Comparisons", "Agreements",
                             "Answer spans", "Invalids", "Redundants",
                             "Warning", "Block").map(<.th(_)).toVdomArray
                      )
                    ),
                    <.tbody(
                      valWorkerInfo.values.toVector.sortBy(-_.numAssignmentsCompleted).map {
                        case wi @ WorkerInfo(
                          workerId, numAssignmentsCompleted,
                          numComparisonInstances, numComparisonAgreements,
                          numAnswerSpans, numInvalids, numRedundants,
                          timeSpent, earnings, warnedAt, blockedAt) =>

                          val numTotalAnswers = numAnswerSpans + numInvalids + numRedundants
                          def percentAs(n: Int) = percent(n, numTotalAnswers)

                          val minutesSpent = timeSpent.toDouble / 1000.0 / 60.0
                          val dollarsPerHour = earnings * 60.0 / minutesSpent
                          val sPerAnswer = timeSpent.toDouble / 1000.0 / numTotalAnswers

                          <.tr(
                            List(workerId, numAssignmentsCompleted.toString, f"$earnings%.2f",
                                 f"$minutesSpent%.2f", f"$dollarsPerHour%.2f", f"$sPerAnswer%.2f",
                                 f"${wi.agreement}%.3f", numComparisonInstances.toString, numComparisonAgreements.toString,
                                 percentAs(numAnswerSpans), percentAs(numInvalids), percentAs(numRedundants),
                                 warnedAt.fold("")(_.toString), blockedAt.fold("")(_.toString)
                            ).map(<.td(_)).toVdomArray
                          )
                      }.toVdomArray
                    )
                  ),
                  <.h3("Recently completed sentences"),
                  lastFewSentences.map {
                    case (sentenceStats, shi @ SentenceHITInfo(sentence, genHITInfos, valHITInfos)) =>
                      import sentenceStats._
                      <.div(
                        ^.padding := "10px",
                        Text.render(sentence),
                        <.div(s"Num keywords: $numKeywords"),
                        <.div(s"Num QA pairs: $numQAPairs"),
                        <.div(s"Num valid QA pairs: $numValidQAPairs"),
                        <.div(s"Generation cost: $generationCost"),
                        <.div(s"Validation cost: $validationCost"),
                        <.div(s"Validation latencies (s): ${validationLatencies.mkString(", ")}"),
                        <.table(
                          ^.borderSpacing := "8px",
                          <.thead(
                            <.tr(
                              List(
                                "Worker ID", "Keyword", "Question", "Answer"
                              ).map(<.td(_)).toVdomArray
                            )
                          ),
                          <.tbody(
                            (for {
                              ValidatedAssignment(genHIT, genAssignment, valAssignments) <- shi.alignValidations
                              validations = valAssignments.map(_.response).transpose
                              (WordedQAPair(keywordIndex, question, answer), qaIndex) <- genAssignment.response.zipWithIndex
                              validationCells = validations(qaIndex).map(va =>
                                <.td(ValidationAnswer.render(sentence, va, genAssignment.response)))
                            } yield <.tr(
                              List(
                                genAssignment.workerId,
                                Text.normalizeToken(sentence(keywordIndex)),
                                question, Text.renderSpan(sentence, answer)
                              ).map(<.td(_)).toVdomArray,
                              validationCells.toVdomArray
                            )).toVdomArray
                          )
                        )
                      )
                  }.toVdomArray
                )
            }
          }
        )
      )
    }
  }

  val FullUI = ScalaComponent.builder[Unit]("Full UI")
    .initialState(State.initial)
    .renderBackend[FullUIBackend]
    .build
}
