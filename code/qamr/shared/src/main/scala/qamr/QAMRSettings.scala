package qamr

object QAMRSettings {

  final def generationBonus(nKeywords: Int, nValidQAs: Int) =
    math.max(0.0, (1 to (nValidQAs - nKeywords)).map(bonusFor).sum)

  final def validationBonus(numQuestions: Int) =
    math.max(0.0, validationBonusPerQuestion * (numQuestions - validationBonusThreshold))

  // used as URL parameters that indicate to the client which interface to use
  val generationTaskKey = "generation"
  val validationTaskKey = "validation"
  val dashboardTaskKey =  "dashboard"

  // annotation pipeline hyperparameters
  val generationReward = 0.20
  val bonusIncrement = 0.03
  def bonusFor(i: Int): Double = bonusIncrement * i + 0.03

  val numKeywords = 4
  val questionCharLimit = 50

  val validationReward = 0.10
  val validationBonusPerQuestion = 0.02
  val validationBonusThreshold = numKeywords

  val generationAccuracyWarningThreshold = 0.8
  val generationAccuracyBlockingThreshold = 0.75
  val generationBufferBeforeWarning = 10
  val generationBufferBeforeBlocking = 10

  val validationAgreementWarningThreshold = 0.75
  val validationAgreementBlockingThreshold = 0.70
  val validationBufferBeforeWarning = 10
  val validationBufferBeforeBlocking = 10
}
