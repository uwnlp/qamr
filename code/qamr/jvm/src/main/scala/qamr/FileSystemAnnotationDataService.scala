package qamr

import spacro.util._

import scala.util.{Try, Success}
import java.nio.file.Path
import java.nio.file.Files

import com.typesafe.scalalogging.StrictLogging

class FileSystemAnnotationDataService(dataPath: Path) extends AnnotationDataService {

  private[this] def getDataDirectoryPath = Try {
    val directory = dataPath
    if(!Files.exists(directory)) {
      Files.createDirectories(directory)
    }
    directory
  }

  private[this] def getFullFilename(name: String) = s"$name.txt"

  override def saveLiveData(name: String, contents: String): Try[Unit] = for {
    directory <- getDataDirectoryPath
    _ <- Try(Files.write(directory.resolve(getFullFilename(name)), contents.getBytes()))
  } yield ()

  import scala.collection.JavaConverters._

  override def loadLiveData(name: String): Try[List[String]] = for {
    directory <- getDataDirectoryPath
    lines <- Try(Files.lines(directory.resolve(getFullFilename(name))).iterator.asScala.toList)
  } yield lines
}
