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

object WriteSquadData extends App {
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

    val squadFormatting = new SquadFormatting

    val trainSquad = squadFormatting.squadFormattedString(
      datasets.train,
      Set("Nikola Tesla", "Oxygen", "Geology", "Genghis Khan", "Imperialism")
    )
    annotationConfig.saveOutputFile("squad-train.json", trainSquad)

    val devSquad = squadFormatting.squadFormattedString(
      datasets.dev,
      Set("Brain", "Emotion")
    )
    annotationConfig.saveOutputFile("squad-dev.json", devSquad)

    val testSquad = squadFormatting.squadFormattedString(
      datasets.test,
      Set("Architecture", "Middle Ages", "Avicenna", "Capacitor", "Martin Luther", "Steam engine")
    )
    annotationConfig.saveOutputFile("squad-test.json", testSquad)

  } finally {
    config.actorSystem.terminate
    import org.slf4j.LoggerFactory
    import ch.qos.logback.classic.LoggerContext
    LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext].stop
  }
}
