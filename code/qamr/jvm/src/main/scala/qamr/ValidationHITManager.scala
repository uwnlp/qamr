package qamr

import qamr._
import qamr.util._

import spacro._
import spacro.tasks._

import scala.collection.mutable
import scala.util.{Try, Success, Failure}

import upickle.default.Reader

import akka.actor.ActorRef

import com.amazonaws.services.mturk.model.AssociateQualificationWithWorkerRequest
import com.amazonaws.services.mturk.model.SendBonusRequest
import com.amazonaws.services.mturk.model.NotifyWorkersRequest

import upickle.default._

import com.typesafe.scalalogging.StrictLogging

object ValidationHITManager {
  def apply[SID : Reader : Writer](
    helper: HITManager.Helper[ValidationPrompt[SID], List[ValidationAnswer]],
    valQualificationTypeId: String,
    generationActor: ActorRef,
    sentenceTrackingActor: ActorRef,
    numAssignmentsForPrompt: ValidationPrompt[SID] => Int,
    initNumHITsToKeepActive: Int)(
    implicit annotationDataService: AnnotationDataService
  ) = {

    new ValidationHITManager[SID](
      helper, valQualificationTypeId, generationActor, sentenceTrackingActor,
      numAssignmentsForPrompt, initNumHITsToKeepActive,
      loadPrompts[SID].iterator)
  }

  val validationPromptsFilename = "validationPrompts"

  def loadPrompts[SID : Reader](
    implicit annotationDataService: AnnotationDataService
  ) = annotationDataService.loadLiveData(validationPromptsFilename)
    .toOption
    .fold(List.empty[ValidationPrompt[SID]])(lines => read[List[ValidationPrompt[SID]]](lines.mkString))

  def notificationEmailText(curAgreement: Double): String = {
    import QAMRSettings._
    val explanatoryText = if(curAgreement < validationAgreementBlockingThreshold) {
      s"""There will be a grace period of several more assignments (${validationBufferBeforeBlocking} more after this calculation was done), and after that, if your agreement rate remains below ${math.round(validationAgreementBlockingThreshold * 100).toInt}%, you will no longer qualify for the task. Note that your qualification value may not accurately reflect your agreement rate: it will be prevented from going below ${math.round(validationAgreementBlockingThreshold * 100).toInt} until the grace period is over."""
    } else {
      s"""You are fine for now, but if this drops below ${math.round(validationAgreementBlockingThreshold * 100).toInt}%, you will no longer qualify for the task. There will be a grace period (${validationBufferBeforeBlocking} more assignments after this calculation was done) during which your qualification value will be prevented from dropping below ${math.round(validationAgreementBlockingThreshold * 100).toInt}."""
    }
    val dropOrRemain = if(curAgreement < validationAgreementBlockingThreshold) "remain" else "drop"
    f"""
The answer judgments that you have provided so far agree with other annotators ${math.round(curAgreement * 10000.0) / 100.0}%.2f%% of the time. $explanatoryText%s

Common reasons for disagreement may include:

  1) Grammaticality. Be sure to mark ungrammatical questions as invalid. Since this is not always clear-cut, try to adjust your standards so that on average, you count about 10%% to 15%% of questions as invalid. (Of course, since it varies by who wrote them, some groups of questions will be worse than others.)
  2) Other rules for validity. Be sure to mark yes/no or either/or questions as invalid, as well as questions which are explicitly about the words in the sentence rather than about the meaning of the sentence. The instructions have several more examples which could be worth revisiting.

Before continuing to work on the task, we suggest carefully reviewing the instructions.

Finally, it is always possible that you got unlucky and were compared to low-quality workers who have not been filtered out yet. If your responses are high-quality, then your agreement rates will likely not $dropOrRemain%s too low and you will be fine. However, because this process is inherently random, we cannot guarantee that no high-quality workers not lose their qualification.
""".trim
  }
}

class ValidationHITManager[SID : Reader : Writer] private (
  helper: HITManager.Helper[ValidationPrompt[SID], List[ValidationAnswer]],
  valQualificationTypeId: String,
  generationActor: ActorRef,
  sentenceTrackingActor: ActorRef,
  numAssignmentsForPrompt: ValidationPrompt[SID] => Int,
  initNumHITsToKeepActive: Int,
  _promptSource: Iterator[ValidationPrompt[SID]])(
  implicit annotationDataService: AnnotationDataService
) extends NumAssignmentsHITManager[ValidationPrompt[SID], List[ValidationAnswer]](
  helper, numAssignmentsForPrompt, initNumHITsToKeepActive, _promptSource) {

  import ValidationHITManager._
  import helper._
  import config._
  import taskSpec.hitTypeId
  import QAMRSettings._

  override lazy val receiveAux2: PartialFunction[Any, Unit] = {
    case SaveData => save
    case Pring => println("Validation manager pringed.")
  }

  override def promptFinished(prompt: ValidationPrompt[SID]): Unit = {
    val assignments = promptToAssignments(prompt)
    sentenceTrackingActor ! ValidationFinished(prompt, assignments)
    val numValid = ValidationAnswer.numValidQuestions(assignments.map(_.response))
    generationActor ! ValidationResult(prompt.genPrompt, prompt.sourceHITId, prompt.sourceAssignmentId, numValid)
    promptToAssignments = promptToAssignments - prompt
  }

  private[this] var allPrompts = loadPrompts[SID]

  override def addPrompt(prompt: ValidationPrompt[SID]): Unit = {
    super.addPrompt(prompt)
    allPrompts = prompt :: allPrompts
  }

  val workerInfoFilename = "validationWorkerInfo"
  val promptToAssignmentsFilename = "promptToAssignments"

  private[this] def save = {
    annotationDataService.saveLiveData(
      workerInfoFilename,
      write[Map[String, WorkerInfo]](allWorkerInfo))
    annotationDataService.saveLiveData(
      promptToAssignmentsFilename,
      write[Map[ValidationPrompt[SID], List[Assignment[List[ValidationAnswer]]]]](promptToAssignments))
    annotationDataService.saveLiveData(
      validationPromptsFilename,
      write[List[ValidationPrompt[SID]]](allPrompts))
    annotationDataService.saveLiveData(
      feedbackFilename,
      write[List[Assignment[List[ValidationAnswer]]]](feedbacks))
    logger.info("Validation data saved.")
  }

  var allWorkerInfo = {
    annotationDataService.loadLiveData(workerInfoFilename)
      .map(_.mkString)
      .map(read[Map[String, WorkerInfo]])
      .toOption.getOrElse {
      // TODO assemble from saved data?
      Map.empty[String, WorkerInfo]
    }
  }

  private[this] var promptToAssignments = {
    annotationDataService.loadLiveData(promptToAssignmentsFilename)
      .map(_.mkString)
      .map(read[Map[ValidationPrompt[SID], List[Assignment[List[ValidationAnswer]]]]])
      .toOption.getOrElse {
      Map.empty[ValidationPrompt[SID], List[Assignment[List[ValidationAnswer]]]]
    }
  }

  val feedbackFilename = "valFeedback"

  var feedbacks =
    annotationDataService.loadLiveData(feedbackFilename)
      .map(_.mkString)
      .map(read[List[Assignment[List[ValidationAnswer]]]])
      .toOption.getOrElse {
      List.empty[Assignment[List[ValidationAnswer]]]
    }

  def tryWarnOrBlock(worker: WorkerInfo): WorkerInfo = {
    if(worker.agreement.isNaN) worker // this means no comparisons have been done yet
    else worker.warnedAt match {
      case None =>
        // set soft qualification since no warning yet
        config.service.associateQualificationWithWorker(
          new AssociateQualificationWithWorkerRequest()
            .withQualificationTypeId(valQualificationTypeId)
            .withWorkerId(worker.workerId)
            .withIntegerValue(math.ceil(100 * math.max(worker.agreement, validationAgreementBlockingThreshold)).toInt)
            .withSendNotification(false))

        if(worker.agreement < validationAgreementWarningThreshold &&
             worker.numAssignmentsCompleted >= validationBufferBeforeWarning) {

          service.notifyWorkers(
            new NotifyWorkersRequest()
              .withSubject("Notification (warning + tips) regarding the question answering task")
              .withMessageText(notificationEmailText(worker.agreement))
              .withWorkerIds(worker.workerId))

          logger.info(s"Validation worker ${worker.workerId} warned at ${worker.numAssignmentsCompleted} with accuracy ${worker.agreement}")
          worker.warned

        } else worker
      case Some(numWhenWarned) =>
        if(worker.numAssignmentsCompleted - numWhenWarned >= validationBufferBeforeBlocking) {
          config.service.associateQualificationWithWorker(
            new AssociateQualificationWithWorkerRequest()
              .withQualificationTypeId(valQualificationTypeId)
              .withWorkerId(worker.workerId)
              .withIntegerValue(math.ceil(100 * worker.agreement).toInt)
              .withSendNotification(false))

          if(math.ceil(worker.agreement).toInt < validationAgreementBlockingThreshold) {

            logger.info(s"Validation worker ${worker.workerId} DQ'd at ${worker.numAssignmentsCompleted} with accuracy ${worker.agreement}")
            worker.blocked
          } else worker
        } else {
          // set soft qualification since still in buffer zone
          config.service.associateQualificationWithWorker(
            new AssociateQualificationWithWorkerRequest()
              .withQualificationTypeId(valQualificationTypeId)
              .withWorkerId(worker.workerId)
              .withIntegerValue(math.ceil(100 * math.max(worker.agreement, validationAgreementBlockingThreshold)).toInt)
              .withSendNotification(false))
          worker
        }
    }
  }

  // override for more interesting review policy
  override def reviewAssignment(hit: HIT[ValidationPrompt[SID]], assignment: Assignment[List[ValidationAnswer]]): Unit = {
    evaluateAssignment(hit, startReviewing(assignment), Approval(""))
    if(!assignment.feedback.isEmpty) {
      feedbacks = assignment :: feedbacks
      logger.info(s"Feedback: ${assignment.feedback}")
    }

    import assignment.workerId

    // grant bonus as appropriate
    val numQuestions = hit.prompt.qaPairs.size
    val totalBonus = validationBonus(numQuestions)
    if(totalBonus > 0.0) {
      service.sendBonus(
        new SendBonusRequest()
          .withWorkerId(workerId)
          .withBonusAmount(f"$totalBonus%.2f")
          .withAssignmentId(assignment.assignmentId)
          .withReason(s"Bonus of ${dollarsToCents(totalBonus)}c awarded for validating $numQuestions questions.")
      )
    }

    var newWorkerInfo = allWorkerInfo
      .get(workerId)
      .getOrElse(WorkerInfo.empty(workerId))
      .addAssignment(assignment.response,
                     assignment.submitTime - assignment.acceptTime,
                     taskSpec.hitType.reward + totalBonus)
    // do comparisons with other workers
    promptToAssignments.get(hit.prompt).getOrElse(Nil).foreach { otherAssignment =>
      val otherWorkerId = otherAssignment.workerId
      val nAgreed = ValidationAnswer.numAgreed(assignment.response, otherAssignment.response)
      // update current worker with comparison
      newWorkerInfo = newWorkerInfo
        .addComparison(numQuestions, nAgreed)
      // update the other one and put back in data structure (warning/blocking if necessary)
      val otherWorkerInfo = tryWarnOrBlock(
        allWorkerInfo(otherWorkerId).addComparison(numQuestions, nAgreed)
      )
      allWorkerInfo = allWorkerInfo.updated(otherWorkerId, otherWorkerInfo)
    }
    // now try just once warning or blocking the current worker before adding everything in
    newWorkerInfo = tryWarnOrBlock(newWorkerInfo)
    allWorkerInfo = allWorkerInfo.updated(workerId, newWorkerInfo)
    promptToAssignments = promptToAssignments.updated(
      hit.prompt,
      assignment :: promptToAssignments.get(hit.prompt).getOrElse(Nil))
  }
}
