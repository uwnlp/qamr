package qamr.util

/** Wrapper class for a predicate that is used to identify stopwords.
  * A wrapper is used so that we can pass this around implicitly without
  * worrying about implicit conversions. */
case class IsStopword(predicate: String => Boolean) extends (String => Boolean) {
  override def apply(token: String): Boolean = predicate(token)
}
