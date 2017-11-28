package qamr

import simulacrum._
import scala.language.implicitConversions

@typeclass trait HasKeyIndices[-A] {
  @op("keyIndices") def getKeyIndices(a: A): Set[Int]
}
