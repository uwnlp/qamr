
import nlpdata.util.Text

import qamr.util._
import spacro._

package object qamr extends PackagePlatformExtensions {

  // prompt/response datatypes for turk tasks and websocket APIs

  case class GenerationPrompt[SID](
    id: SID,
    keywords: List[Int])

  type GenerationResponse = List[WordedQAPair]

  case class GenerationApiRequest[SID](id: SID)
  case class GenerationApiResponse(tokens: Vector[String])

  // prompt for validation
  case class ValidationPrompt[SID](
    genPrompt: GenerationPrompt[SID],
    sourceHITId: String,
    sourceAssignmentId: String,
    qaPairs: List[WordedQAPair]
  ) {
    def id = genPrompt.id
  }

  type ValidationResponse = List[ValidationAnswer]

  case class ValidationApiRequest[SID](id: SID)
  case class ValidationApiResponse(sentence: Vector[String])
}
