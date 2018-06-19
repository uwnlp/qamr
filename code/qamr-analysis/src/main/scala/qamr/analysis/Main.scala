package qamr.analysis

import qamr.example._
import qamr.util._
import qamr._

import spacro._
import spacro.tasks._
import spacro.util._

import akka.pattern.ask

import scala.concurrent.duration._

import cats.implicits._

import com.amazonaws.services.mturk._
import com.amazonaws.services.mturk.model._

import nlpdata.util.Text
import nlpdata.util.HasTokens.ops._

import nlpdata.datasets.wiktionary.Inflections
import nlpdata.datasets.wiktionary.WiktionaryFileSystemService

import java.nio.file.Paths

object Main extends App {
  val domain = "localhost"
  val isProduction = false
  val interface = "0.0.0.0"
  val httpPort = 8888
  val httpsPort = 8080

  val rootPath = java.nio.file.Paths.get(".")
  val dataPath = rootPath.resolve("data/example")
  val annotationPath = dataPath.resolve("annotations")
  val resourcePath = rootPath.resolve("datasets")

  implicit val timeout = akka.util.Timeout(5.seconds)
  implicit val config: TaskConfig = {
    if(isProduction) {
      val hitDataService = new FileSystemHITDataService(annotationPath.resolve("production"))
      ProductionTaskConfig("qamr-example", domain,
                           interface, httpPort, httpsPort,
                           hitDataService)
    } else {
      val hitDataService = new FileSystemHITDataService(annotationPath.resolve("sandbox"))
      SandboxTaskConfig("qamr-example", domain,
                        interface, httpPort, httpsPort,
                        hitDataService)
    }
  }

  try {
    val annotationConfig = new AnnotationConfig(dataPath, resourcePath)
    import annotationConfig.SentenceIdHasTokens

    val datasets = new Datasets(Paths.get("../data"))

    val trainDev = datasets.train |+| datasets.dev
    val allUnfiltered = datasets.trainFull |+| datasets.devFull |+| datasets.testFull

    val Wiktionary = new WiktionaryFileSystemService(Paths.get("datasets/wiktionary"))

    implicit val inflections = Wiktionary.getInflectionsForTokens(
      trainDev.sentenceToQAs.keys.iterator.flatMap(_.tokens)
    )

    val extPhrases = new ExternalPhrases(annotationConfig, trainDev)
    val validation = new Validation(allUnfiltered)
    val srlComparison = new SRLComparison(annotationConfig, datasets.ptb)

    println(extPhrases.fullReport)
    println

    println(validation.report)
    println

    println(srlComparison.pbRecallReport)
    println
    println(srlComparison.nbRecallReport)
    println
    println(srlComparison.qasrlRecallReport)

  } finally {
    config.actorSystem.terminate
    import org.slf4j.LoggerFactory
    import ch.qos.logback.classic.LoggerContext
    LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext].stop
  }
}
