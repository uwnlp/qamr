package qamr.analysis

import cats.implicits._

import qamr.DataFiles
import qamr.QAData
import qamr.example.SentenceId

import java.nio.file.Path
import java.nio.file.Files

class Datasets(dataRoot: Path) {

  def readDataTSV(pathStr: String): QAData[SentenceId] = {
    import scala.collection.JavaConverters._
    DataFiles.readTSV(
      Files.lines(dataRoot.resolve(pathStr)).iterator.asScala.toList,
      SentenceId.fromString
    ).get
  }

  lazy val trainFull = readDataTSV("full/train.tsv")
  lazy val devFull = readDataTSV("full/dev.tsv")
  lazy val testFull = readDataTSV("full/test.tsv")
  lazy val ptbFull = readDataTSV("full/ptb.tsv")

  lazy val train = readDataTSV("filtered/train.tsv")
  lazy val dev = readDataTSV("filtered/dev.tsv")
  lazy val test = readDataTSV("filtered/test.tsv")
  lazy val ptb = readDataTSV("filtered/ptb.tsv")

}
