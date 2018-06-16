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

class AnnotationConfig(
  dataPath: Path,
  resourcePath: Path)(
  implicit config: TaskConfig) { // TODO check if config is actually needed here

  private[this] val liveDataPath = dataPath.resolve("live")
  val liveAnnotationDataService = new FileSystemAnnotationDataService(liveDataPath)

  val staticDataPath = dataPath.resolve("static")

  def saveOutputFile(name: String, contents: String): Try[Unit] = Try {
    val directory = staticDataPath.resolve("out")
    if(!Files.exists(directory)) {
      Files.createDirectories(directory)
    }
    val path = directory.resolve(name)
    Files.write(path, contents.getBytes())
  }

  def loadOutputFile(name: String): Try[List[String]] = Try {
    val directory = staticDataPath.resolve("out")
    if(!Files.exists(directory)) {
      Files.createDirectories(directory)
    }
    val path = directory.resolve(name)
    import scala.collection.JavaConverters._
    Files.lines(path).iterator.asScala.toList
  }

  def loadInputFile(name: String): Try[List[String]] = Try {
    val directory = staticDataPath.resolve("in")
    if(!Files.exists(directory)) {
      Files.createDirectories(directory)
    }
    val path = directory.resolve(name)
    import scala.collection.JavaConverters._
    Files.lines(path).iterator.asScala.toList
  }

  val PTB = new PTBFileSystemService(
    resourcePath.resolve("ptb")
  )

  val Wiki1k = new Wiki1kFileSystemService(
    resourcePath.resolve("wiki1k")
  )

  implicit object SentenceIdHasTokens extends HasTokens[SentenceId] {
    def getTokens(id: SentenceId): Vector[String] = id match {
      case PTBSentenceId(path) => PTB.getSentence(path).get.tokens
      case WikiSentenceId(path) => Wiki1k.getSentence(path).get.tokens
    }
  }

  private[this] val conservativeStopwordFilename = "english-stop-conservative.txt"

  /** Stopword set from a local file.
    *
    * Not sure where the file came from, but I found it in my old repo
    * from Dan Garrette's undergrad NLP class.
    * I deleted some of the stopwords that we actually want.
    */
  lazy val conservativeStopwords: Set[LowerCaseString] = {
    val wordLines = loadInputFile(conservativeStopwordFilename).get.toSet
    (wordLines ++ Set("hm", "uh", "um")).map(_.lowerCase)
  }

  /** (non-normalized as well as normalized PTB tokens.) */
  val punctuation = Set[String](
    ".", ",", "!", "?", ";", ":", "...",
    "''", "\"", "'", "`", "``",
    "#", "--", "-", "–", "—", "%", // PTB dashes, hyphens, en and em dashes
    "−", // minus sign (unicode hex 2122)
    "+", "±", "<", "≤", "≥", ">", "=",
    "^", "@", "|", "&",
    "/.", "/?", "/", "\\",
    ")", "]", "}",
    "(", "[", "{",
    "-RRB-", "-RCB-", "-RSB-",
    "-LRB-", "-LCB-", "-LSB-")

  val contractions = Set("n't", "'s", "'re", "'ve", "'ll", "na", "'m", "'d")

  val questionWords = Set("who", "what", "when", "where", "why", "how",
                          "whose", "which", "much", "many")

  val pronouns = Set(
    "I", "me", "my", "mine",
    "we", "us", "our", "ours",
    "you", "your", "yours",
    "he", "him", "his",
    "she", "her", "hers",
    "it", "its",
    "they", "them", "their",
    "someone", "something",
    "this", "that"
  ).map(_.lowerCase)

  lazy val reallyUninterestingTokens = conservativeStopwords ++ punctuation ++ contractions ++ questionWords

  def isReallyUninteresting(t: String) = reallyUninterestingTokens.contains(t) ||
    reallyUninterestingTokens.contains(t.toLowerCase)

  implicit val isStopword = IsStopword(isReallyUninteresting)

  // data for experiment

  lazy val origQASRLPaths = read[Vector[PTBSentencePath]](
    loadInputFile("origQASRLPaths.txt").get.head
  )
}
