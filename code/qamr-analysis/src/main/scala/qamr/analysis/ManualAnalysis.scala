package qamr.analysis

class ManualAnalysis(filepath: String) {
  def histogramString[A: Ordering](hist: Scorer[A, Int]): String = {
    val vec = hist.keyIterator.toList.sorted.map(a => a -> hist.get(a)).toVector
    val max = vec.map(_._2).max
    val scaleMax = 50.0
    val scaleFactor = scaleMax / max
    def scale(n: Int): Int = math.ceil(n.toDouble * scaleFactor).toInt
    def pounds(n: Int) = "#" * n
    vec
      .map { case (a, n) => f"$a%10s|${pounds(scale(n))}%s $n%d"}
      .mkString("\n")
  }

  val fullLabels = io.Source.fromFile(filepath).getLines
    .map(l => l.split("\\t").head).toList.tail
    .filter(_ != "==")

  val argTypeLabels = List(
    "VCORE", "VMOD", "NCORE", "NMOD", "APPCO",
    "COPCO", "COPMOD", "NUM", "COEXACT", "COPART",
    "NAME", "OTHER", "REL", "DET", "AMT")
  // missing: SURF, APPMOD, ADJMOD, ADVMOD. not present in the sample
  val labels = fullLabels.map(fl => argTypeLabels.find(fl.contains).get)
  val total = labels.size
  val labelsHist = Scorer[String, Int](labels)

  import ManualAnalysis._
  def stat(f: String => Boolean) = pctString(fullLabels.count(f), total)

  def p(f: String => Boolean) = f

  val isInferred = p(_ == "REL")
  val isDirect = not(isInferred)

  val isImplicit = p(_.contains("-IMP"))
  val hasCoref = p(_.contains("-CO"))
  val hasSyntacticVariation = p(_.contains("-VAR"))
  val hasSynonym = p(_.contains("-SYN"))

  val isExpressionChanged = isInferred or isImplicit or hasCoref or hasSyntacticVariation or hasSynonym
  val isExpressionUnchanged = not(isExpressionChanged)

  val report = f"""
    |Total: $total%d
    |${histogramString(labelsHist)}
    |Directly expressed: ${stat(isDirect)}%s
    |Inferred: ${stat(isInferred)}%s
    |
    |Expressed with same syntactic relationship: ${stat(isExpressionUnchanged)}
    |Syntactic variation: ${stat(hasSyntacticVariation)}
    |Uses a synonym: ${stat(hasSynonym)}
    |Coreferent answer present: ${stat(hasCoref)}
    |Relation is implicit in sentence: ${stat(isImplicit)}
    |Inferred: ${stat(isInferred)}%s
    |""".stripMargin.trim
}
object ManualAnalysis {
  implicit class PredicateOps[A](val f: A => Boolean) extends AnyVal {
    def and(g: A => Boolean) = (a: A) => f(a) && g(a)
    def or(g: A => Boolean) = (a: A) => f(a) || g(a)
  }
  def not[A](f: A => Boolean) = (a: A) => !f(a)
}
