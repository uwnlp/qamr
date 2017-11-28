package qamr.util

/** Represents a histogram of integers.
  * I have a feeling we can get rid of this in favor of Scorer anyway...
  */
case class IntHist private (map: Map[Int, Int]) {
  def add(instance: Int) = new IntHist(IntHist.addInstance(map, instance))
  def addAll(instances: TraversableOnce[Int]) = new IntHist(instances.foldLeft(map)(IntHist.addInstance))

  def mean = {
    val (sum, count) = map.foldLeft((0, 0)) {
      case ((sum, count), (i, freq)) => (sum + (i * freq), count + freq)
    }
    if(count == 0) None
    else Some(sum.toDouble / count)
  }
  def variance = mean.map { thisMean =>
    val (sse, count) = map.foldLeft((0, 0)) {
      case ((sse, count), (i, freq)) => (sse + (math.pow(i - thisMean, 2).toInt * freq), count + freq)
    }
    sse.toDouble / count
  }
  def stdev = variance.map(math.sqrt)
}

object IntHist {
  private def addInstance(map: Map[Int, Int], instance: Int) =
    map.updated(instance, map.get(instance).getOrElse(0) + 1)

  def empty: IntHist = new IntHist(Map.empty[Int, Int].withDefaultValue(0))
  def apply(entries: Iterator[Int]): IntHist = new IntHist(
    entries.foldLeft(Map.empty[Int, Int].withDefaultValue(0))(addInstance)
  )
}
