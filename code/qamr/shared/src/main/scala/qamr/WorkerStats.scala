package qamr

import qamr.util._

/** Data structure to keep track of a single worker's stats on the generation task. */
case class WorkerStats(
  workerId: String,
  numAssignmentsCompleted: Int,
  numQAPairsWritten: Int,
  numQAPairsValid: Int,
  timeSpent: Long,
  earnings: Double,
  warnedAt: Option[Int],
  blockedAt: Option[Int]) {

  def accuracy = numQAPairsValid.toDouble / numQAPairsWritten

  def addAssignment(
    numWritten: Int,
    numValid: Int,
    timeTaken: Long,
    totalReward: Double
  ) = WorkerStats(
    workerId,
    numAssignmentsCompleted + 1,
    numQAPairsWritten + numWritten,
    numQAPairsValid + numValid,
    timeSpent + timeTaken,
    earnings + totalReward,
    warnedAt, blockedAt)

  def warned = this.copy(warnedAt = Some(numAssignmentsCompleted))
  def blocked = this.copy(blockedAt = Some(numAssignmentsCompleted))
}
object WorkerStats {
  def empty(workerId: String) = WorkerStats(workerId, 0, 0, 0, 0L, 0.0, None, None)
}
