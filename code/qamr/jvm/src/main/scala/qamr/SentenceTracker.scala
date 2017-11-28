package qamr

import qamr._

import spacro._
import spacro.tasks._

import nlpdata.util._
import nlpdata.util.HasTokens.ops._

import scala.util.Try

import akka.actor.Actor

import upickle.default._

import com.typesafe.scalalogging.StrictLogging

sealed trait TrackingUpdate[SID]
case class GenerationFinished[SID](prompt: GenerationPrompt[SID]) extends TrackingUpdate[SID]
case class ValidationBegun[SID](prompt: ValidationPrompt[SID]) extends TrackingUpdate[SID]
case class ValidationFinished[SID](
  prompt: ValidationPrompt[SID],
  assignments: List[Assignment[List[ValidationAnswer]]]
) extends TrackingUpdate[SID]

class SentenceTracker[SID : Reader : Writer : HasTokens : HasKeyIndices](
  genHITTypeId: String,
  valHITTypeId: String)(
  implicit config: TaskConfig,
  annotationDataService: AnnotationDataService
) extends Actor with StrictLogging {

  import QAMRSettings._

  val finishedSentenceStatsFilename = "finishedSentenceStats"
  var finishedSentenceStats: List[SentenceStats[SID]] =
    annotationDataService.loadLiveData(finishedSentenceStatsFilename)
      .map(_.mkString)
      .map(read[List[SentenceStats[SID]]])
      .toOption.getOrElse {
      // TODO assemble from saved data?
      List.empty[SentenceStats[SID]]
    }

  val aggregateSentenceStatsFilename = "aggregateSentenceStats"
  var aggregateSentenceStats: AggregateSentenceStats =
    annotationDataService.loadLiveData(aggregateSentenceStatsFilename)
      .map(_.mkString)
      .map(read[AggregateSentenceStats])
      .toOption.getOrElse {
      AggregateSentenceStats.aggregate(finishedSentenceStats)
    }

  val sentenceStatusesFilename = "sentenceStatuses"
  var sentenceStatuses: Map[SID, SentenceStatus[SID]] =
    annotationDataService.loadLiveData(sentenceStatusesFilename)
      .map(_.mkString)
      .map(read[Map[SID, SentenceStatus[SID]]])
      .toOption.getOrElse {
      // TODO assemble from saved data?
      Map.empty[SID, SentenceStatus[SID]]
    }

  def save = {
    annotationDataService.saveLiveData(
      finishedSentenceStatsFilename,
      write[List[SentenceStats[SID]]](finishedSentenceStats))
    annotationDataService.saveLiveData(
      sentenceStatusesFilename,
      write[Map[SID, SentenceStatus[SID]]](sentenceStatuses))
    annotationDataService.saveLiveData(
      aggregateSentenceStatsFilename,
      write[AggregateSentenceStats](aggregateSentenceStats))
    logger.info("Sentence tracker saved.")
  }

  def processUpdate(id: SID, update: TrackingUpdate[SID]) = {
    val newStatus = {
      val res = sentenceStatuses
        .get(id)
        .getOrElse(emptyStatus(id))
      update match {
        case GenerationFinished(gPrompt) => res.withKeywords(gPrompt.keywords.toSet)
        case ValidationBegun(vPrompt) => res.beginValidation(vPrompt)
        case ValidationFinished(vPrompt, assignments) => res.finishValidation(vPrompt, assignments)
      }
    }

    if(newStatus.isFinished) {
      val newStats = SentenceTracker.makeStats(newStatus, genHITTypeId, valHITTypeId)
      finishedSentenceStats =  newStats :: finishedSentenceStats
      aggregateSentenceStats = aggregateSentenceStats.add(newStats)
      sentenceStatuses = sentenceStatuses - id
    } else {
      sentenceStatuses = sentenceStatuses.updated(id, newStatus)
    }
  }

  override def receive = {
    case SaveData => save
    case Pring => println("Sentence tracker pringed.")
    case u: TrackingUpdate[SID] => u match {
      case u @ GenerationFinished(gPrompt) => processUpdate(gPrompt.id, u)
      case u @ ValidationBegun(vPrompt) => processUpdate(vPrompt.genPrompt.id, u)
      case u @ ValidationFinished(vPrompt, _) => processUpdate(vPrompt.genPrompt.id, u)
    }
  }
}

object SentenceTracker {
  def makeStats[SID : HasTokens : Reader](
    status: SentenceStatus[SID],
    genHITTypeId: String,
    valHITTypeId: String)(
    implicit config: TaskConfig
  ): SentenceStats[SID] = {
    val allValidations = status.finishedAssignments
    val id = status.id
    val sentence = id.tokens
    val allValHITIds = allValidations.map(_.hitId).toSet
    val valHITInfos = allValHITIds.toList
      .map(hitId => config.hitDataService.getHITInfo[ValidationPrompt[SID], List[ValidationAnswer]](valHITTypeId, hitId).get)
    val allGenHITIds = valHITInfos.map(_.hit.prompt.sourceHITId).toSet
    val genHITInfos = allGenHITIds.toList
      .map(hitId => config.hitDataService.getHITInfo[GenerationPrompt[SID], List[WordedQAPair]](genHITTypeId, hitId).get)
    val sentenceHITInfo = SentenceHITInfo(sentence, genHITInfos, valHITInfos)

    val earliestTime = Try(
      genHITInfos.flatMap(_.assignments).map(_.acceptTime).min
    ).toOption.getOrElse(0L)
    val latestTime = Try(
      (valHITInfos.flatMap(_.assignments).map(_.submitTime) ++
         genHITInfos.flatMap(_.assignments).map(_.submitTime)).max
    ).toOption.getOrElse(0L)

    val alignedValidations = sentenceHITInfo.alignValidations
    val allKeywords = genHITInfos.map(_.hit.prompt.keywords).flatten.toSet
    val qaPairsEachKeywordPrompt = for {
      HITInfo(hit, assignments) <- genHITInfos
      assignment <- assignments
      keywordIndex <- hit.prompt.keywords.toList
    } yield assignment.response.filter(_.wordIndex == keywordIndex).size
    val qaPairsEachKeywordActual = for {
      keywordIndex <- allKeywords.toList
    } yield {
      val qaPairs = for {
        HITInfo(hit, assignments) <- genHITInfos
        assignment <- assignments
        wqa @ WordedQAPair(_, question, answerIndices) <- assignment.response
        if (answerIndices contains keywordIndex) || question.toLowerCase.contains(sentence(keywordIndex).toLowerCase)
      } yield wqa
      qaPairs.size
    }
    val validationLatencies = for {
      HITInfo(_, assignments) <- genHITInfos
      assignment <- assignments
      validations = for {
        HITInfo(valHIT, valAssignments) <- valHITInfos
        if valHIT.prompt.sourceAssignmentId == assignment.assignmentId
      } yield valAssignments.map(_.submitTime).max
      if !validations.isEmpty
      completion = validations.max
    } yield ((completion - assignment.submitTime) / 1000L).toInt  // seconds

    val numQAPairs = genHITInfos.flatMap(_.assignments).flatMap(_.response).size
    val numValidQAPairs = alignedValidations
      .map(av => ValidationAnswer.numValidQuestions(av.valAssignments.map(_.response)))
      .sum
    val completionTime = Try(
      valHITInfos.flatMap(_.assignments).map(_.submitTime).max
    ).toOption.getOrElse(0L)
    val genCost = alignedValidations.map(_.genCost).sum
    val valCost = alignedValidations.map(_.valCost).sum
    val genHITIds = genHITInfos.map(_.hit.hitId).toSet
    val valHITIds = valHITInfos.map(_.hit.hitId).toSet
    SentenceStats(
      id,
      earliestTime, latestTime,
      allKeywords.size,
      numQAPairs,
      numValidQAPairs,
      qaPairsEachKeywordPrompt,
      qaPairsEachKeywordActual,
      validationLatencies,
      completionTime,
      genCost, valCost,
      genHITIds, valHITIds)
  }
}
