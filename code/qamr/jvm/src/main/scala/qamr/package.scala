package qamr

import qamr.HasKeyIndices.ops._
import qamr.util.IsStopword

import spacro.Assignment

trait PackagePlatformExtensions {

  // message definitions

  case object Pring

  case class ValidationResult[SID](
    prompt: GenerationPrompt[SID],
    sourceHITId: String,
    sourceAssignmentId: String,
    numValid: Int)

  case object SaveData

  // functions for chunking sentences into groups of content words

  def splitNum(n: Int): List[Int] =
    if(n <= 0) Nil
    else if(n <= 3) List(n)
    else if(n == 5) List(2, 3)
    else if(n == 6) List(3, 3)
    else if(n == 9) List(3, 3, 3)
    else 4 :: splitNum(n - 4)

  def splitList(l: List[Int]) = splitNum(l.size)
    .foldLeft((l, List.empty[List[Int]])) {
    case ((remaining, groups), groupSize) =>
      (remaining.drop(groupSize), remaining.take(groupSize) :: groups)
  }._2

  def tokenSplits(
    tokens: Vector[String])(
    implicit isStopword: IsStopword
  ) = splitList(tokens.indices.filter(i => !isStopword(tokens(i))).toList)

  // need key indices to construct empty status objects

  def emptyStatus[SID : HasKeyIndices](id: SID) = {
    SentenceStatus(
      id, id.keyIndices,
      Set.empty[Int], Set.empty[ValidationPrompt[SID]],
      List.empty[Assignment[List[ValidationAnswer]]])
  }
}
