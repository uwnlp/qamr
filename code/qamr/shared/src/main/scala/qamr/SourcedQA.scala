package qamr

/** Holds all information relevant to a single QA pair from the data. */
case class SourcedQA[SID](
  id: QAPairId[SID],
  wqa: WordedQAPair,
  validatorResponses: List[(String, ValidationAnswer)] // pairs with validator worker ID
) {
  def validatorAnswers: List[ValidationAnswer] = validatorResponses.map(_._2)
  def goodValAnswers = validatorAnswers.flatMap(_.getAnswer.map(_.indices))
  def isValid = validatorAnswers.forall(_.isAnswer)

  def question = wqa.question
  def answers = wqa.answer :: goodValAnswers
}
