package qamr

import spacro._
import spacro.tasks._

import qamr._
import qamr.util._

import scala.collection.mutable
import scala.util.{Try, Success, Failure}

import upickle.default.Reader

import akka.actor.ActorRef

import com.amazonaws.services.mturk.model.AssociateQualificationWithWorkerRequest
import com.amazonaws.services.mturk.model.SendBonusRequest
import com.amazonaws.services.mturk.model.NotifyWorkersRequest
import upickle.default._

import com.typesafe.scalalogging.StrictLogging

case class FlagBadSentence[SID](id: SID)

object GenerationHITManager {
  def notificationEmailText(curAccuracy: Double) = {
    import QAMRSettings._
    val explanatoryText = if(curAccuracy < generationAccuracyBlockingThreshold) {
      s"""There will be a grace period of several more assignments (${generationBufferBeforeBlocking} more after this calculation was done), and after that, if your accuracy remains below ${math.round(generationAccuracyBlockingThreshold * 100).toInt}%, you will no longer qualify for the task. Note that your qualification value may not accurately reflect your accuracy: it will be prevented from going below ${math.round(generationAccuracyBlockingThreshold * 100).toInt} until the grace period is over."""
    } else {
      s"""You are fine for now, but if this drops below ${math.round(generationAccuracyBlockingThreshold * 100).toInt}%, you will no longer qualify for the task. There will be a grace period (${generationBufferBeforeBlocking} more assignments after this calculation was done) during which your qualification value will be prevented from dropping below ${math.round(generationAccuracyBlockingThreshold * 100).toInt}."""
    }
    val dropOrRemain = if(curAccuracy < generationAccuracyBlockingThreshold) "remain" else "drop"
    f"""
Of your question-answer pairs that have been reviewed so far, ${math.round(curAccuracy * 10000.0) / 100.0}%.2f%% were judged valid or non-redundant by validators. $explanatoryText%s

If you are having trouble writing grammatical questions for all of the words you are given, keep a few things in mind:

  1) You can use a special word in either the question or the answer. Sometimes it is hard to form a nice question-answer pair one way, but it is very easy to do it the other way.
  2) The answer can contain more than just the special word. Especially with proper names that contain several words, you may be able to use that full name as the answer to a few questions, and spread those question-answer pairs over the set of special words you were given.

Also be sure not to write any redundant questions. Before you continue, we suggest that you carefully read over the instructions again to maximize the rewards you can get out of the task and minimize the chance you lose your qualification. If you haven't already, it could be a good idea to try the other task, titled "Answer simple questions about a sentence". Doing its qualification test and some HITs could help give you an idea of how to come up with and write good questions.

Finally, it is always possible that you got unlucky. If your responses are high-quality, then your accuracy will likely not $dropOrRemain%s too low. However, because this process is inherently random, we cannot guarantee that no high-quality workers will lose their qualification.
""".trim
  }
}

class GenerationHITManager[SID : Reader : Writer](
  helper: HITManager.Helper[GenerationPrompt[SID], List[WordedQAPair]],
  genQualificationTypeId: String,
  validationHelper: HITManager.Helper[ValidationPrompt[SID], List[ValidationAnswer]],
  validationActor: ActorRef,
  sentenceTrackingActor: ActorRef,
  numAssignmentsForPrompt: GenerationPrompt[SID] => Int,
  initNumHITsToKeepActive: Int,
  _promptSource: Iterator[GenerationPrompt[SID]])(
  implicit annotationDataService: AnnotationDataService
) extends NumAssignmentsHITManager[GenerationPrompt[SID], List[WordedQAPair]](
  helper, numAssignmentsForPrompt, initNumHITsToKeepActive, _promptSource
) with StrictLogging {

  import GenerationHITManager._
  import helper._
  import config._
  import QAMRSettings._
  import taskSpec.hitTypeId

  override def promptFinished(prompt: GenerationPrompt[SID]): Unit = {
    sentenceTrackingActor ! GenerationFinished(prompt)
  }

  val badSentenceIdsFilename = "badSentenceIds"

  var badSentences = annotationDataService.loadLiveData(badSentenceIdsFilename)
    .map(_.mkString)
    .map(read[Set[SID]])
    .toOption.getOrElse {
    Set.empty[SID]
  }

  private[this] def flagBadSentence(id: SID) = {
    badSentences = badSentences + id
    save
    for {
      (prompt, hitInfos) <- activeHITInfosByPromptIterator
      if prompt.id == id
      HITInfo(hit, _) <- hitInfos
    } yield helper.expireHIT(hit)
  }

  val workerStatsFilename = "generationWorkerStats"

  var allWorkerStats =
    annotationDataService.loadLiveData(workerStatsFilename)
      .map(_.mkString)
      .map(read[Map[String, WorkerStats]])
      .toOption.getOrElse {
      // TODO assemble from saved data
      Map.empty[String, WorkerStats]
    }

  val feedbackFilename = "genFeedback"

  var feedbacks =
    annotationDataService.loadLiveData(feedbackFilename)
      .map(_.mkString)
      .map(read[List[Assignment[List[WordedQAPair]]]])
      .toOption.getOrElse {
      List.empty[Assignment[List[WordedQAPair]]]
    }

  private[this] def save = {
    annotationDataService.saveLiveData(
      workerStatsFilename,
      write[Map[String, WorkerStats]](allWorkerStats))
    annotationDataService.saveLiveData(
      feedbackFilename,
      write[List[Assignment[List[WordedQAPair]]]](feedbacks))
    annotationDataService.saveLiveData(
      badSentenceIdsFilename,
      write[Set[SID]](badSentences))
    logger.info("Generation data saved.")
  }

  override def reviewAssignment(hit: HIT[GenerationPrompt[SID]], assignment: Assignment[List[WordedQAPair]]): Unit = {
    evaluateAssignment(hit, startReviewing(assignment), Approval(""))
    if(!assignment.feedback.isEmpty) {
      feedbacks = assignment :: feedbacks
      logger.info(s"Feedback: ${assignment.feedback}")
    }
    val validationPrompt = ValidationPrompt(hit.prompt, hit.hitId, assignment.assignmentId, assignment.response)
    validationActor ! validationHelper.Message.AddPrompt(validationPrompt)
    sentenceTrackingActor ! ValidationBegun(validationPrompt)
  }

  override lazy val receiveAux2: PartialFunction[Any, Unit] = {
    case SaveData => save
    case Pring => println("Generation manager pringed.")
    case fbs: FlagBadSentence[SID] => fbs match {
      case FlagBadSentence(id) => flagBadSentence(id)
    }
    case vr: ValidationResult[SID] => vr match {
      case ValidationResult(prompt: GenerationPrompt[SID], hitId, assignmentId, numQAsValid) =>
        val ha = for {
          hit <- hitDataService.getHIT[GenerationPrompt[SID]](hitTypeId, hitId).toOptionLogging(logger).toList
          assignment <- hitDataService.getAssignmentsForHIT[List[WordedQAPair]](hitTypeId, hitId).get
          if assignment.assignmentId == assignmentId
        } yield (hit, assignment)

        ha.foreach { case (hit, assignment) =>
          // award bonuses
          val numSpecialWords = prompt.keywords.size
          val numQAsProvided = assignment.response.size
          val bonusAwarded = generationBonus(numSpecialWords, numQAsValid)
          if(bonusAwarded > 0.0) {
            service.sendBonus(
              new SendBonusRequest()
                .withWorkerId(assignment.workerId)
                .withBonusAmount(f"$bonusAwarded%.2f")
                .withAssignmentId(assignment.assignmentId)
                .withReason(s"""$numQAsValid out of $numQAsProvided question-answer pairs were judged to be valid,
                  where at least $numSpecialWords were required, for a bonus of
                  ${dollarsToCents(bonusAwarded)}c.""".replace("\\s+", " "))
            )
          }

          val stats = allWorkerStats
            .get(assignment.workerId)
            .getOrElse(WorkerStats.empty(assignment.workerId))
            .addAssignment(assignment.response.size, numQAsValid,
                           assignment.submitTime - assignment.acceptTime,
                           taskSpec.hitType.reward + bonusAwarded)

          // update qualifications according to performance
          val newStats = stats.warnedAt match {
            case None =>
              // set soft qualification since no warning yet
              config.service.associateQualificationWithWorker(
                new AssociateQualificationWithWorkerRequest()
                  .withQualificationTypeId(genQualificationTypeId)
                  .withWorkerId(assignment.workerId)
                  .withIntegerValue(math.ceil(100 * math.max(stats.accuracy, generationAccuracyBlockingThreshold)).toInt)
                  .withSendNotification(false))
              if(stats.accuracy < generationAccuracyWarningThreshold &&
                   stats.numAssignmentsCompleted >= generationBufferBeforeWarning) {

                service.notifyWorkers(
                  new NotifyWorkersRequest()
                    .withSubject("Notification (warning + tips) regarding the question-answer task")
                    .withMessageText(notificationEmailText(stats.accuracy))
                    .withWorkerIds(assignment.workerId))

                logger.info(s"Generation worker ${assignment.workerId} warned at ${stats.numAssignmentsCompleted} with accuracy ${stats.accuracy}")
                stats.warned

              } else stats
            case Some(numWhenWarned) =>
              if(stats.numAssignmentsCompleted - numWhenWarned >= generationBufferBeforeBlocking) {

                config.service.associateQualificationWithWorker(
                  new AssociateQualificationWithWorkerRequest()
                    .withQualificationTypeId(genQualificationTypeId)
                    .withWorkerId(assignment.workerId)
                    .withIntegerValue(math.ceil(100 * stats.accuracy).toInt)
                    .withSendNotification(false))

                if(math.ceil(stats.accuracy).toInt < generationAccuracyBlockingThreshold) {
                  logger.info(s"Generation worker ${assignment.workerId} DQ'd at ${stats.numAssignmentsCompleted} with accuracy ${stats.accuracy}")
                  stats.blocked
                } else stats

              } else {
                // set soft qualification since still in buffer zone
                config.service.associateQualificationWithWorker(
                  new AssociateQualificationWithWorkerRequest()
                    .withQualificationTypeId(genQualificationTypeId)
                    .withWorkerId(assignment.workerId)
                    .withIntegerValue(math.ceil(100 * math.max(stats.accuracy, generationAccuracyBlockingThreshold)).toInt)
                    .withSendNotification(false))

                stats
              }
          }
          allWorkerStats = allWorkerStats.updated(assignment.workerId, newStats)
        }
    }

  }
}
