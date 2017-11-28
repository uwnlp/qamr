package qamr

import scala.util.Try

trait AnnotationDataService {

  def saveLiveData(name: String, contents: String): Try[Unit]

  def loadLiveData(name: String): Try[List[String]]
}
