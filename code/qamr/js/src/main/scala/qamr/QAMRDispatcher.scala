package qamr

import spacro.tasks._

import scalajs.js
import scalajs.js.JSApp
import org.scalajs.jquery.jQuery

import japgolly.scalajs.react.vdom.html_<^.VdomTag

import upickle.default._

abstract class QAMRDispatcher[SID : Reader : Writer] extends TaskDispatcher {

  def generationInstructions: VdomTag
  def requireWhAtQuestionBeginning: Boolean
  def validationInstructions: VdomTag

  lazy val genClient = new GenerationClient[SID](
    generationInstructions,
    requireWhAtQuestionBeginning)

  lazy val valClient = new ValidationClient[SID](
    validationInstructions)

  val dashClient: DashboardClient[SID] = new DashboardClient[SID]()

  final override lazy val taskMapping = Map[String, () => Unit](
    QAMRSettings.generationTaskKey -> genClient.main,
    QAMRSettings.validationTaskKey -> valClient.main,
    QAMRSettings.dashboardTaskKey -> dashClient.main)

}
