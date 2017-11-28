package qamr

/** Used to uniquely index into a single question-answer pair
  * gathered in the turk task.
  */
case class QAPairId[SID](
  sentenceId: SID,
  keywords: List[Int],
  workerId: String,
  qaIndex: Int)
