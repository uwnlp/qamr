package qamr.example

import qamr.FileSystemAnnotationDataService
import qamr.GenerationPrompt
import qamr.AnnotationPipeline
import qamr.util.IsStopword

import spacro.tasks.TaskConfig

import nlpdata.datasets.ptb.PTBFileSystemService
import nlpdata.datasets.ptb.PTBSentencePath
import nlpdata.datasets.wiki1k.Wiki1kFileSystemService
import nlpdata.datasets.wiki1k.Wiki1kPath
import nlpdata.util.HasTokens
import nlpdata.util.HasTokens.ops._
import nlpdata.util.LowerCaseStrings._
import nlpdata.util.Text

import scala.util.Try
import scala.util.Random

import upickle.default._

import java.nio.file.{Files, Path, Paths}

/** Replicates the annotation setup for the paper. */
class AnnotationSetup(
  dataPath: Path,
  resourcePath: Path)(
  implicit config: TaskConfig) {

  val annotationConfig = new AnnotationConfig(dataPath, resourcePath)
  import annotationConfig._

  val numPTB = 150

  lazy val (ptbTrain, ptbDev, ptbTest) = {
    val shuffleRand = new Random(832592735L)
    val (train, devTestRest) = shuffleRand.shuffle(origQASRLPaths).splitAt(numPTB * 4 / 5)
    val (dev, testRest) = devTestRest.splitAt(numPTB / 10)
    val test = testRest.take(numPTB / 10)
    (train, dev, test)
  }

  lazy val ptb100ForAMR = PTB.allPTBSentencePaths.get.take(103).map(PTBSentenceId.apply).toList

  def getWikiSentences(rand: Random, filePaths: Vector[Wiki1kPath], numSentences: Int) = {
    rand.shuffle(
      filePaths.flatMap(p => Wiki1k.getFile(p).get.paragraphs)
    ).filter(p =>
      !p.exists(sentence =>
        sentence.tokens.exists(t =>
          Text.normalizeToken(t) == "\\"))
    ).flatten.map(s => s.path).take(numSentences)
  }

  val numWikipedia = 2500

  lazy val (wikipediaTrain, wikipediaDev, wikipediaTest) = {
    val shuffleRand = new Random(1230976L)
    val (trainFiles, devTestRestFiles) = shuffleRand.shuffle(
      Wiki1k.wiki1kPathsForDomain("wikipedia")
    ).splitAt(640)
    val (devFiles, testRestFiles) = devTestRestFiles.splitAt(80)
    val testFiles = testRestFiles.take(80)

    val train = getWikiSentences(shuffleRand, trainFiles, numWikipedia * 4 / 5)
    val dev = getWikiSentences(shuffleRand, devFiles, numWikipedia / 10)
    val test = getWikiSentences(shuffleRand, testFiles, numWikipedia / 10)
    (train, dev, test)
  }

  val numWikinews = 2500

  lazy val (wikinewsTrain, wikinewsDev, wikinewsTest) = {
    val shuffleRand = new Random(1246902L)
    val (trainFiles, devTestRestFiles) = shuffleRand.shuffle(
      Wiki1k.wiki1kPathsForDomain("wikinews")
        .sortBy(-_.suffix.toInt) // relies on wikinews IDs being ints... true as of now
        .take(1000) // remove 1k most recent b/c not as well audited / lower quality
    ).splitAt(800)
    val (devFiles, testRestFiles) = devTestRestFiles.splitAt(80)
    val testFiles = testRestFiles.take(80)

    val train = getWikiSentences(shuffleRand, trainFiles, numWikinews * 4 / 5)
    val dev = getWikiSentences(shuffleRand, devFiles, numWikinews / 10)
    val test = getWikiSentences(shuffleRand, testFiles, numWikinews / 10)
    (train, dev, test)
  }

  lazy val trainIds = ptbTrain.map(PTBSentenceId(_): SentenceId) ++
    wikipediaTrain.map(WikiSentenceId(_): SentenceId) ++
    wikinewsTrain.map(WikiSentenceId(_): SentenceId)
  lazy val trainIDSet = trainIds.toSet
  def isTrain(id: SentenceId) = trainIDSet.contains(id)

  lazy val devIds = ptbDev.map(PTBSentenceId(_): SentenceId) ++
    wikipediaDev.map(WikiSentenceId(_): SentenceId) ++
    wikinewsDev.map(WikiSentenceId(_): SentenceId)
  lazy val devIDSet = devIds.toSet
  def isDev(id: SentenceId) = devIDSet.contains(id)

  lazy val testIds = ptbTest.map(PTBSentenceId(_): SentenceId) ++
    wikipediaTest.map(WikiSentenceId(_): SentenceId) ++
    wikinewsTest.map(WikiSentenceId(_): SentenceId)
  lazy val testIDSet = testIds.toSet
  def isTest(id: SentenceId) = testIDSet.contains(id)

  lazy val sourceIds = {
    val idShuffleRand = new Random(218469L)
    idShuffleRand.shuffle(trainIds ++ devIds ++ testIds)
      .filter {
      case WikiSentenceId(path) =>
        !path.filePath.suffix.contains("785582") && // this is apparently a French interview
          !path.filePath.suffix.contains("648587") // this is apparently a Spanish article
      case _ => true
    }
  }

  // the AMR sentences are prepended here because they were incorporated part-way through the original annotation
  // and I wanted to keep the other values all the same after including them
  lazy val allIds = (ptb100ForAMR ++ sourceIds).toVector

  def numGenerationAssignmentsForPrompt(p: GenerationPrompt[SentenceId]) = p.id match {
    case PTBSentenceId(_) => 5
    case id @ WikiSentenceId(_) => if(isTrain(id)) 1 else 3
  }

  lazy val experiment = new AnnotationPipeline(
    allIds, numGenerationAssignmentsForPrompt,
    liveAnnotationDataService, isStopword,
    qualTest = ExampleQualTest)
}
