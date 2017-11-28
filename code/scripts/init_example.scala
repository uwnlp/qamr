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

// NOTE: change the two values below between runs or hosts.
val domain = "localhost" // change to domain of production server
val isProduction = false // sandbox. change to true for production

val rootPath = java.nio.file.Paths.get(".")
val dataPath = rootPath.resolve("data/example")
val annotationPath = dataPath.resolve("annotations")
val resourcePath = rootPath.resolve("datasets")

implicit val timeout = akka.util.Timeout(5.seconds)
implicit val config: TaskConfig = {
  if(isProduction) {
    val hitDataService = new FileSystemHITDataService(annotationPath.resolve("production"))
    ProductionTaskConfig("qamr-example", domain, hitDataService)
  } else {
    val hitDataService = new FileSystemHITDataService(annotationPath.resolve("sandbox"))
    SandboxTaskConfig("qamr-example", domain, hitDataService)
  }
}

val setup = new qamr.example.AnnotationSetup(dataPath, resourcePath)
val exp = setup.experiment
val data = new DataExporter(setup)

// call to start the server so the previews are up at
// <domain>:<port>/task/{generation,validation,dashboard}/preview
def init = {
  exp.server
  exp.genManager
  ()
}

def exit = {
  // actor system has to be terminated for JVM to be able to terminate properly upon :q
  config.actorSystem.terminate
  // flush & release logging resources
  import org.slf4j.LoggerFactory
  import ch.qos.logback.classic.LoggerContext
  LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext].stop
  System.out.println("Terminated actor system and logging. Type :q to end.")
}

// turk operations / convenience functions

def deleteAll = {
  exp.setGenHITsActive(0)
  exp.setValHITsActive(0)
  Thread.sleep(2000)
  exp.update
  Thread.sleep(2000)
  exp.expire
  Thread.sleep(2000)
  exp.delete
}

def yesterday = {
  val cal = java.util.Calendar.getInstance
  cal.add(java.util.Calendar.DATE, -1)
  cal.getTime
}

import scala.collection.JavaConverters._

def expireHITById(hitId: String) = {
  config.service.updateExpirationForHIT(
    (new UpdateExpirationForHITRequest)
      .withHITId(hitId)
      .withExpireAt(yesterday))
}

def approveAllAssignmentsByHITId(hitId: String) = for {
  mTurkAssignment <- config.service.listAssignmentsForHIT(
    new ListAssignmentsForHITRequest()
      .withHITId(hitId)
      .withAssignmentStatuses(AssignmentStatus.Submitted)
    ).getAssignments.asScala.toList
} yield config.service.approveAssignment(
  new ApproveAssignmentRequest()
    .withAssignmentId(mTurkAssignment.getAssignmentId)
    .withRequesterFeedback(""))

def deleteHITById(hitId: String) =
  config.service.deleteHIT((new DeleteHITRequest).withHITId(hitId))

def disableHITById(hitId: String) = {
  expireHITById(hitId)
  deleteHITById(hitId)
}

def getActiveHITIds = {
  config.service.listAllHITs
    .filter(hit => hit.getHITTypeId == exp.genTaskSpec.hitTypeId || hit.getHITTypeId == exp.valTaskSpec.hitTypeId)
    .map(_.getHITId)
}

// use to detect orphan HITs with different HIT IDs, particularly to disable/delete them.
// However, this will also include any other unrelated HITs you have up on MTurk, so use with care.
def getAllActiveHITIds = {
  config.service.listAllHITs.map(_.getHITId)
}
