package qamr.analysis

object Tokenizer {
  /** Tokenizes an English string. */
  def tokenize(s: String): Vector[String] = {
    import java.io.StringReader
    import edu.stanford.nlp.process.PTBTokenizer
    import edu.stanford.nlp.process.WordTokenFactory
    import scala.collection.JavaConverters._
    new PTBTokenizer(new StringReader(s), new WordTokenFactory(), "")
      .tokenize.asScala.toVector.map(_.word)
  }
}
