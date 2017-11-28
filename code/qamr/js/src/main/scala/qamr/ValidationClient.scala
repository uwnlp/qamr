package qamr

import spacro.tasks._
import spacro.ui._

import cats.implicits._

import qamr.util._

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

class ValidationClient[SID : Writer : Reader](
  instructions: VdomTag)(
  implicit promptReader: Reader[ValidationPrompt[SID]], // macro serializers don't work for superclass constructor parameters
  responseWriter: Writer[List[ValidationAnswer]] // same as above
) extends TaskClient[ValidationPrompt[SID], List[ValidationAnswer], Service.UnitRequest] {

  import QAMRSettings._

  def main(): Unit = jQuery { () =>
    Styles.addToDocument()
    FullUI().renderIntoDOM(dom.document.getElementById(FieldLabels.rootClientDivLabel))
  }

  val WebsocketLoadableComponent = new WebsocketLoadableComponent[ValidationApiRequest[SID], ValidationApiResponse]
  import WebsocketLoadableComponent._
  val HighlightingComponent = new HighlightingComponent[(Int, Int)]
  import HighlightingComponent._

  lazy val questions = prompt.qaPairs.map(_.question)

  @Lenses case class State(
    curQuestion: Int,
    isInterfaceFocused: Boolean,
    answers: List[ValidationAnswer])
  object State {
    def initial = State(0, false, questions.map(_ => Answer(Set.empty[Int])))
  }

  class FullUIBackend(scope: BackendScope[Unit, State]) {
    def updateResponse: Callback = scope.state.map { state =>
      setResponse(state.answers)
    }

    def updateHighlights(hs: HighlightingState) = {
      val span = hs.span
      scope.modState(
        State.answers.modify(answers =>
          answers.zipWithIndex.map {
            case (Answer(_), i) => Answer(span.filter(_._1 == i).map(_._2))
            case (invalidOrRedundant, _) => invalidOrRedundant
          }
        )
      )
    }

    def handleKey(e: ReactKeyboardEvent): Callback = {
      def next = scope.modState(State.curQuestion.modify(i => (i + 1) % questions.size))
      def prev = scope.modState(State.curQuestion.modify(i => (i + questions.size - 1) % questions.size))
      if(isNotAssigned) {
        Callback.empty
      } else CallbackOption.keyCodeSwitch(e) {
        case KeyCode.Down => next
        case KeyCode.Up => prev
      } >> e.preventDefaultCB
    }

    def qaField(s: State, sentence: Vector[String], span: Set[(Int, Int)])(index: Int) = {
      val isFocused = s.curQuestion == index
      val answer = s.answers(index)
      def highlightedSpanFor(i: Int) = Answer(span.filter(_._1 == i).map(_._2))

      <.div(
        ^.overflow := "hidden",
        <.div(
          Styles.unselectable,
          ^.float := "left",
          ^.margin := "1px",
          ^.padding := "1px",
          ^.minHeight := "1px",
          ^.border := "1px solid",
          ^.borderRadius := "2px",
          ^.textAlign := "center",
          ^.width := "50px",
          (^.backgroundColor := "#E01010").when(answer.isInvalid),
          ^.onClick --> scope.modState(
            State.answers.modify(answers =>
              answers.updated(
                index,
                if(answers(index).isInvalid) highlightedSpanFor(index)
                else InvalidQuestion)
            )
          ),
          "Invalid"
        ),
        <.span(
          Styles.bolded.when(isFocused),
          Styles.uncomfortableOrange.when(s.answers(s.curQuestion).getRedundant.fold(false)(_.other == index)),
          Styles.unselectable,
          ^.float := "left",
          ^.margin := "1px",
          ^.padding := "1px",
          ^.onClick --> scope.modState(s =>
            if(s.curQuestion == index || !s.answers(index).isAnswer) s
            else if(s.answers(s.curQuestion).getRedundant.fold(false)(_.other == index)) {
              State.answers.modify(answers => answers.updated(s.curQuestion, highlightedSpanFor(s.curQuestion)))(s)
            } else {
              State.answers.modify(answers => answers.updated(s.curQuestion, Redundant(index)))(s)
            }
          ),
          questions(index)
        ),
        <.div(
          Styles.answerIndicator,
          Styles.unselectable,
          ^.float := "left",
          ^.minHeight := "1px",
          ^.width := "25px",
          "-->".when(isFocused)
        ),
        <.div(
          ^.float := "left",
          ^.margin := "1px",
          ^.padding := "1px",
          (^.color := "#CCCCCC").when(answer.getAnswer.fold(true)(_.indices.isEmpty)),
          answer match {
            case InvalidQuestion => "N/A"
            case Redundant(other) => <.span("Redundant with ", <.i(questions(other)))
            case Answer(span) if span.isEmpty && isFocused =>
              "Highlight answer above, move with arrow keys, or click on a redundant question"
            case Answer(span) => Text.render(
              sentence.zipWithIndex.filter(p => span.contains(p._2)).map(_._1))
          }
        )
      )
    }

    def render(state: State) = {
      WebsocketLoadable(
        WebsocketLoadableProps(
          websocketURI = websocketUri, request = ValidationApiRequest(prompt.id), render = {
            case Connecting => <.div("Connecting to server...")
            case Loading => <.div("Retrieving data...")
            case Loaded(ValidationApiResponse(sentence), _) =>
              import state._
              Highlighting(
                HighlightingProps(
                  isEnabled = !isNotAssigned && answers(curQuestion).isAnswer, update = updateHighlights, render = {
                    case (hs @ HighlightingState(spans, status), HighlightingContext(_, startHighlight, startErase, stopHighlight, touchElement)) =>
                      val showHighlights = answers(curQuestion).isAnswer
                      val curSpan = spans.collect { case (`curQuestion`, i) => i }
                      def touchWord(i: Int) = touchElement((curQuestion, i))
                      <.div(
                        ^.onMouseUp --> stopHighlight,
                        ^.onMouseDown --> startHighlight,
                        Styles.mainContent,
                        <.p(<.b("")),
                        <.p(<.span(Styles.badRed, """ Please read the detailed instructions at the bottom before you begin. """),
                            """ To begin working on this HIT, please request the question answering agreement qualification
                                (it is auto-granted) and take the qualification test.
                                Also, while there may be few HITs available at any one time,
                                more will be continuously uploaded
                                as other workers write questions for you to validate. """),
                        <.hr(),
                        <.div(
                          ^.tabIndex := 0,
                          ^.onFocus --> scope.modState(State.isInterfaceFocused.set(true)),
                          ^.onBlur --> scope.modState(State.isInterfaceFocused.set(false)),
                          ^.onKeyDown ==> handleKey,
                          ^.position := "relative",
                          <.div(
                            ^.position := "absolute",
                            ^.top := "20px",
                            ^.left := "0px",
                            ^.width := "100%",
                            ^.height := "100%",
                            ^.textAlign := "center",
                            ^.color := "rgba(48, 140, 20, .3)",
                            ^.fontSize := "48pt",
                            (if(isNotAssigned) "Accept assignment to start" else "Click here to start")
                          ).when(!isInterfaceFocused),
                          <.p(
                            Styles.unselectable,
                            Text.render(
                              sentence.indices.toList,
                              getToken = (index: Int) => sentence(index),
                              spaceFromNextWord = (nextIndex: Int) => List(
                                <.span(
                                  ^.backgroundColor := (
                                    if(showHighlights && curSpan.contains(nextIndex) && curSpan.contains(nextIndex - 1)) {
                                      "#FFFF00"
                                    } else "transparent"),
                                  " ")),
                              renderWord = (index: Int) => List(
                                <.span(
                                  ^.backgroundColor := (
                                    if(showHighlights && curSpan.contains(index)) "#FFFF00"
                                    else "transparent"),
                                  ^.onMouseMove --> touchWord(index),
                                  ^.onMouseDown ==> (
                                    (e: ReactEvent) => if(curSpan.contains(index)) {
                                      e.stopPropagation // so we don't trigger the global startHighlight
                                      startErase >> touchWord(index)
                                    } else {
                                      startHighlight >> touchWord(index)
                                    }
                                  ),
                                  Text.normalizeToken(sentence(index))
                                ))
                            ).toVdomArray),
                          <.ul(
                            Styles.listlessList,
                            (0 until questions.size)
                              .map(qaField(state, sentence, hs.span))
                              .map(field => <.li(^.display := "block", field))
                              .toVdomArray
                          ),
                          <.p(s"Bonus: ${dollarsToCents(validationBonus(questions.size))}c")
                        ),
                        <.p(
                          <.input(
                            ^.`type` := "text",
                            ^.name := FieldLabels.feedbackLabel,
                            ^.placeholder := "Feedback? (Optional)",
                            ^.margin := "1px",
                            ^.padding := "1px",
                            ^.width := "484px"
                          )
                        ),
                        <.input(
                          ^.`type` := "submit",
                          ^.disabled := !state.answers.forall(_.isComplete),
                          ^.id := FieldLabels.submitButtonLabel,
                          ^.value := "submit"),
                        instructions
                      )
                  }
                )
              )
          }
        )
      )
    }
  }

  val FullUI = ScalaComponent.builder[Unit]("Full UI")
    .initialState(State.initial)
    .renderBackend[FullUIBackend]
    .componentDidUpdate(_.backend.updateResponse)
    .build

}
