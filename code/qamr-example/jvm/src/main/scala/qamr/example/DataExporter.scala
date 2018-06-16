package qamr.example

import cats.Foldable
import cats.implicits._

import qamr.DataFiles

import spacro.HITInfo
import spacro.tasks.TaskConfig

class DataExporter(
  setup: AnnotationSetup)(
  implicit config: TaskConfig
) {

  import setup.annotationConfig.SentenceIdHasTokens

  val allGenInfos = setup.experiment.allGenInfos
  val allValInfos = setup.experiment.allValInfos

  val workerAnonymizationMap: Map[String, String] = {
    val allGenWorkerIdsIter = for {
      HITInfo(_, assignments) <- allGenInfos.iterator
      a <- assignments
    } yield a.workerId

    val allValWorkerIdsIter = for {
      HITInfo(_, assignments) <- allValInfos.iterator
      a <- assignments
    } yield a.workerId

    val allWorkerIds = (allGenWorkerIdsIter ++ allValWorkerIdsIter).toSet

    val rand = new scala.util.Random(1543754734L)
    val randomOrderedWorkerIds = rand.shuffle(allWorkerIds.toVector)
    randomOrderedWorkerIds.zipWithIndex.map {
      case (workerId, index) => workerId -> index.toString
    }.toMap
  }


  val wikiIds = setup.allIds.collect {
    case id @ WikiSentenceId(_) => id: SentenceId
  }

  val trainIds = wikiIds.filter(setup.isTrain)

  val devIds = wikiIds.filter(setup.isDev)

  val testIds = wikiIds.filter(setup.isTest)

  val ptbIds = setup.allIds.collect {
    case id @ PTBSentenceId(_) => id: SentenceId
  }

  def makeFullTSV[F[_]: Foldable](ids: F[SentenceId]): String =
    DataFiles.makeFinalQAPairTSV(ids, SentenceId.toString, workerAnonymizationMap, allGenInfos, allValInfos, false)

  def makeFilteredTSV[F[_]: Foldable](ids: F[SentenceId]): String =
    DataFiles.makeFinalQAPairTSV(ids, SentenceId.toString, workerAnonymizationMap, allGenInfos, allValInfos, true)

  def makeReadableTSV[F[_]: Foldable](ids: F[SentenceId]): String =
    DataFiles.makeFinalReadableQAPairTSV(ids, SentenceId.toString, workerAnonymizationMap, allGenInfos, allValInfos, true)

  def writeFinalSentenceIndex = {
    setup.annotationConfig.saveOutputFile("wiki-sentences.tsv", DataFiles.makeSentenceIndex(wikiIds, SentenceId.toString))
  }

  def writeFinalFullTSVs = {
    setup.annotationConfig.saveOutputFile("full/train.tsv", makeFullTSV(trainIds))
    setup.annotationConfig.saveOutputFile("full/dev.tsv", makeFullTSV(devIds))
    setup.annotationConfig.saveOutputFile("full/test.tsv", makeFullTSV(testIds))
    setup.annotationConfig.saveOutputFile("full/ptb.tsv", makeFullTSV(ptbIds))
  }

  def writeFinalFilteredTSVs = {
    setup.annotationConfig.saveOutputFile("filtered/train.tsv", makeFilteredTSV(trainIds))
    setup.annotationConfig.saveOutputFile("filtered/dev.tsv", makeFilteredTSV(devIds))
    setup.annotationConfig.saveOutputFile("filtered/test.tsv", makeFilteredTSV(testIds))
    setup.annotationConfig.saveOutputFile("filtered/ptb.tsv", makeFilteredTSV(ptbIds))
  }

  def writeFinalReadableTSVs = {
    setup.annotationConfig.saveOutputFile("readable/train.tsv", makeReadableTSV(trainIds))
    setup.annotationConfig.saveOutputFile("readable/dev.tsv", makeReadableTSV(devIds))
    setup.annotationConfig.saveOutputFile("readable/test.tsv", makeReadableTSV(testIds))
  }

  def writeAllTSVs = {
    writeFinalFullTSVs
    writeFinalFilteredTSVs
    writeFinalReadableTSVs
    writeFinalSentenceIndex
  }

}
