package qamr

import qamr._

import spacro._

/** Stores all data relevant to an assignment completed by a turker on the generation task
  * that has been fully validated by validators.
  * This is used in the dashboard. */
case class ValidatedAssignment[SID](
  genHIT: HIT[GenerationPrompt[SID]],
  genAssignment: Assignment[List[WordedQAPair]],
  valAssignments: List[Assignment[List[ValidationAnswer]]]) {
  def genCost: Double = {
    val numValidQs = ValidationAnswer.numValidQuestions(valAssignments.map(_.response))
    QAMRSettings.generationReward + QAMRSettings.generationBonus(genHIT.prompt.keywords.size, numValidQs)
  }
  def valCost: Double = valAssignments.size * (QAMRSettings.validationReward + QAMRSettings.validationBonus(genAssignment.response.size))
}

/** Stores all data relevant to a sentence
  * for display in the summary in the dashboard.
  */
case class SentenceHITInfo[SID](
  tokens: Vector[String],
  genHITInfos: List[HITInfo[GenerationPrompt[SID], List[WordedQAPair]]],
  valHITInfos: List[HITInfo[ValidationPrompt[SID], List[ValidationAnswer]]]) {

  def alignValidations: List[ValidatedAssignment[SID]] = for {
    HITInfo(genHIT, genAssignments) <- genHITInfos
    genAssignment <- genAssignments
    HITInfo(valHIT, valAssignments) <- valHITInfos
    if valHIT.prompt.sourceHITId == genHIT.hitId && valHIT.prompt.sourceAssignmentId == genAssignment.assignmentId
  } yield ValidatedAssignment(genHIT, genAssignment, valAssignments)
}

/** Used to summarize current task state and recent annotation results in the dashboard. */
case class SummaryInfo[SID](
  // generation
  val numGenActive: Int,
  val genWorkerStats: Map[String, WorkerStats],
  val genFeedback: List[Assignment[List[WordedQAPair]]],
  // validation
  val numValPromptsWaiting: Int,
  val numValActive: Int,
  val valWorkerInfo: Map[String, WorkerInfo],
  val valFeedback: List[Assignment[List[ValidationAnswer]]],
  // final results
  val lastFewSentences: Map[SentenceStats[SID], SentenceHITInfo[SID]],
  val aggSentenceStats: AggregateSentenceStats)
