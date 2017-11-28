package qamr

import monocle.macros._

/** A QA pair written by a turker on the generation task.
  * includes the index of the word that they were required to include
  * in the question or answer.
  */
@Lenses case class WordedQAPair(wordIndex: Int, question: String, answer: Set[Int])
