package qamr.analysis

import qamr.QAData
import qamr.SourcedQA

import qamr.example.AnnotationConfig
import qamr.example.SentenceId
import qamr.example.PTBSentenceId

import nlpdata.datasets.ptb._
import nlpdata.datasets.propbank._
import nlpdata.datasets.nombank._
import nlpdata.datasets.qasrl._
import nlpdata.datasets.wiktionary.Inflections
import nlpdata.structure._

import nlpdata.util.HasTokens
import nlpdata.util.HasTokens.ops._
import nlpdata.util.LowerCaseStrings._
import nlpdata.util.Text

import cats.implicits._

class SRLComparison(
  annotationConfig: AnnotationConfig,
  data: QAData[SentenceId])(
  implicit sidHasTokens: HasTokens[SentenceId],
  inflections: Inflections
) {

  def getWordsInQuestion(
    sentence: Vector[String],
    string: String)(
    implicit inflections: Inflections
  ): Set[Int] = {
    val tokens = Tokenizer.tokenize(string).filterNot(annotationConfig.isReallyUninteresting)
    val moreTokens = tokens.map(t => Text.normalizeToken(t).lowerCase).flatMap(inflections.getAllForms)
    val generalizedTokens = tokens.map(_.lowerCase) ++ moreTokens
    sentence.zipWithIndex.filter(p => generalizedTokens.contains(p._1.lowerCase)).map(_._2).toSet
  }

  lazy val ptbData = data.filterBySentence(_.isPTB)

  import SRLComparison._

  def alignToPAS(
    tokens: Vector[String],
    qas: List[SourcedQA[SentenceId]],
    paStructures: List[PredicateArgumentStructure]): PASAlignment = {

    // println(s"Sentence:\n${Text.render(tokens)}")
    def breakIntoContiguous(s: Set[Int]): List[Set[Int]] = {
      if(s.isEmpty) Nil else {
        val min = s.min
        var max = s.min + 1
        while(s.contains(max)) {
          max = max + 1
        }
        val firstInterval = (min until max).toSet
        firstInterval :: breakIntoContiguous(s -- firstInterval)
      }
    }
    // println(s"Questions:\n${qas.map(_.question).mkString("\n")}")
    val allContiguousSpans = qas.flatMap { sqa =>
      val qSpan = getWordsInQuestion(tokens, sqa.question)
      (qSpan :: sqa.answers).flatMap(breakIntoContiguous)
    }
    // println(s"Contiguous spans:\n${allContiguousSpans.map(Text.renderSpan(tokens, _)).mkString("\n")}")
    val minimalContiguousSpans = allContiguousSpans.filter(span =>
      !allContiguousSpans.exists(otherSpan =>
        otherSpan.subsetOf(span) && !span.subsetOf(otherSpan)
      )
    ).toSet
    val minimalSpanQuestionAppearanceCounts = Scorer[Set[Int], Int](
      qas.flatMap { qa =>
        val qSpan = getWordsInQuestion(tokens, qa.question)
        breakIntoContiguous(qSpan).filter(minimalContiguousSpans.contains)
      }
    )
    // println(s"Minimal contiguous spans:\n${minimalContiguousSpans.map(Text.renderSpan(tokens, _)).mkString("\n")}")
    val minimalSpanAllAppearanceCounts = Scorer[Set[Int], Int](
      allContiguousSpans.filter(minimalContiguousSpans.contains)
    )
    val spansByPredicateness = {
      val spanVec = minimalContiguousSpans.toVector
      spanVec.zip(spanVec.map(s => minimalSpanQuestionAppearanceCounts(s).toDouble / minimalSpanAllAppearanceCounts(s)))
        .sortBy(-_._2)
        .map(_._1)
    }
    // println(s"Spans by salience:\n${spansByPredicateness.map(Text.renderSpan(tokens, _)).mkString("\n")}")

    val allPredArgs = paStructures.flatMap(getRelevantPredArgs)
    val alignedQAs = qas.map { sqa =>
      // println(s"QA Pair:\t${sqa.question}\t${sqa.answers.map(Text.renderSpan(tokens, _))}")
      val questionWords = getWordsInQuestion(tokens, sqa.question)
      val questionPAs = for {
        qNode <- spansByPredicateness.filter(_.subsetOf(questionWords))
        pa @ PredArg(pred, arg) <- allPredArgs
        .sortBy { pa =>
          val argSpan = pa.arg.words.map(_.index).toSet
            -1.0 * sqa.answers.map(a => a.intersect(argSpan).size.toDouble / a.union(argSpan).size).meanOpt.get
        }
        if qNode.contains(pred.head.index)
        argSpan = arg.words.map(_.index).toSet
        if(sqa.answers.filter(a => a.intersect(argSpan).nonEmpty)).size >= 1
      } yield pa
      val predArgOpt = questionPAs.headOption.orElse {
        val answerPAs = for {
          qNode <- spansByPredicateness.filter(_.subsetOf(questionWords))
          pa @ PredArg(pred, arg) <- allPredArgs
          argSpan = arg.words.map(_.index).toSet
          if qNode.subsetOf(argSpan)
          if(sqa.answers.filter(a => a.contains(pred.head.index))).size > 1
        } yield pa
        answerPAs.headOption
      }
      // println(s"PredArg alignment: $predArgOpt")
      sqa -> predArgOpt
    }.toMap
    val numQAsAligned = alignedQAs.values.flatten.size
    val pasCovered = alignedQAs.values.flatten.toSet
    val numPredictions = qas.size
    // println(s"PAs covered: $pasCovered")

    val missedDeps = allPredArgs.filterNot(pasCovered)

    val pr = PrecisionRecall(
      numPredicted = numPredictions,
      numGold = allPredArgs.size,
      numCorrect = numQAsAligned,
      numCovered = pasCovered.size)

    PASAlignment(allPredArgs, alignedQAs, pr)
  }

  def propBankPR(path: PropBankSentencePath, tokens: Vector[String], qas: List[SourcedQA[SentenceId]]) = {
    val pbSentence = PropBank.getSentence(path).get
    val paStructures = pbSentence.predicateArgumentStructures
    alignToPAS(tokens, qas, paStructures)
  }

  lazy val numPropBankSentences = {
    val pbPaths = for {
      PTBSentenceId(path) <- ptbData.sentenceToQAs.keys.iterator
      pbPath <- PropBank.ptbToPropBankSentencePath(path).toOption.iterator
    } yield pbPath
    pbPaths.toSet.size
  }

  def allPropBankPRs(n: Int = 1) = {
    val res = for {
      (id @ PTBSentenceId(path), qas) <- ptbData.sentenceToQAs.iterator
      pbPath <- PropBank.ptbToPropBankSentencePath(path).toOption.iterator
      tokens = id.tokens
      sampledQAs = sampleQAPairs(qas, n)
    } yield propBankPR(pbPath, tokens, sampledQAs.toList).stats
    res.toList
  }

  lazy val pbRecalls = (1 to 5).map(i => List.fill(6 - i)(allPropBankPRs(i).reduce(_ aggregate _).recall))
  lazy val pbRecallDists = pbRecalls.map(r => (r.meanOpt.get, r.stdevSampleOpt.get))
  lazy val pbRecallReport = s"PropBank:\nNumber of sentences: $numPropBankSentences\n" + pbRecallDists.zip(1 to 5)
    .map { case ((mean, stdev), n) => f"$n%d annotators: $mean%.4f ± $stdev%.4f" }
    .mkString("\n")

  def nomBankPR(path: PTBSentencePath, tokens: Vector[String], qas: List[SourcedQA[SentenceId]]) = {
    val pas = NomBank.getPredArgStructuresReindexed(path).get
    alignToPAS(tokens, qas, pas)
  }

  def allNomBankPRs(n: Int = 1) = {
    val res = for {
      (id @ PTBSentenceId(path), qas) <- ptbData.sentenceToQAs.iterator
      tokens = id.tokens
      sampledQAs = sampleQAPairs(qas, n)
    } yield nomBankPR(path, tokens, sampledQAs.toList).stats
    res.toList
  }.toList

  lazy val numNomBankSentences = ptbData.sentenceToQAs.keys.size

  lazy val nbRecalls = (1 to 5).map(i => List.fill(6 - i)(allNomBankPRs(i).reduce(_ aggregate _).recall))
  lazy val nbRecallDists = nbRecalls.map(r => (r.meanOpt.get, r.stdevSampleOpt.get))
  lazy val nbRecallReport = s"NomBank:\nNumber of sentences: $numNomBankSentences\n" + nbRecallDists.zip(1 to 5)
    .map { case ((mean, stdev), n) => f"$n%d annotators: $mean%.4f ± $stdev%.4f" }
    .mkString("\n")

  lazy val qasrl = QASRL.getQASRL.get

  // assumes path is stored
  def qasrlPR(path: PTBSentencePath, tokens: Vector[String], qas: List[SourcedQA[SentenceId]]) = {
    val qasrlSentence = qasrl(path)
    alignToPAS(tokens, qas, qasrlSentence.predicateArgumentStructures)
  }

  lazy val numQASRLSentences = ptbData.sentenceToQAs.keys
    .collect { case PTBSentenceId(path) => path }
    .filter(qasrl.keySet.contains)
    .size

  def allQASRLPRs(n: Int = 1) = {
    val res = for {
      (id @ PTBSentenceId(path), qas) <- ptbData.sentenceToQAs.iterator
      if qasrl.keySet.contains(path)
      tokens = id.tokens
      sampledQAs = sampleQAPairs(qas, n)
    } yield qasrlPR(path, tokens, sampledQAs.toList).stats
    res.toList
  }

  lazy val qasrlRecalls = (1 to 5).map(i => List.fill(6 - i)(allQASRLPRs(i).reduce(_ aggregate _).recall))
  lazy val qasrlRecallDists = qasrlRecalls.map(r => (r.meanOpt.get, r.stdevSampleOpt.get))
  lazy val qasrlRecallReport = s"QA-SRL:\nNumber of sentences: $numQASRLSentences\n" + qasrlRecallDists.zip(1 to 5)
    .map { case ((mean, stdev), n) => f"$n%d annotators: $mean%.4f ± $stdev%.4f" }
    .mkString("\n")

  def writeMissedDeps = {
    val sb = new StringBuilder
    val shuffleRand = new util.Random(821569L)
    val shuffledSentences = shuffleRand.shuffle(ptbData.sentenceToQAs.keys.toVector)
    for (id @ PTBSentenceId(path) <- shuffledSentences; if qasrl.keySet.contains(path)) {
      val qas = ptbData.sentenceToQAs(id)
      val tokens = id.tokens

      val pbAlignmentOpt = PropBank.ptbToPropBankSentencePath(path)
        .toOption.map(propBankPR(_, tokens, qas))
      val nbAlignment = nomBankPR(path, tokens, qas)
      val qasrlAlignment = qasrlPR(path, tokens, qas)

      def addPA(pa: PredArg): Unit = pa match { case PredArg(pred, arg) =>
        sb.append(s"\t${pred.head.token} (${pred.head.index}) --" + arg.label + "-> ")
        sb.append(Text.render(arg.words.map(_.token)) + "\n")

        // find relevant QA pairs
        val relevantQAs = qas.filter { qa =>
          val qWordsFromSentence = getWordsInQuestion(tokens, qa.question)
          val allQAIndices = qWordsFromSentence ++ qa.answers.reduce(_ union _)
          val relevantToPred = (
            allQAIndices.contains(pred.head.index) ||
              inflections.getAllForms(pred.head.token.lowerCase).map(_.toString)
              .exists(qa.question.toLowerCase.contains))
          val relevantToArg = (
            allQAIndices.intersect(arg.words.map(_.index).toSet).nonEmpty ||
              qa.question.toLowerCase.contains(
                Text.renderSpan(tokens, arg.words.map(_.index).toSet).toLowerCase
              ))
          relevantToPred & relevantToArg
        }

        relevantQAs.foreach { qa =>
          val answers = qa.answers.map(Text.renderSpan(tokens, _)).distinct.mkString(" / ")
          sb.append(s"|\t${qa.question}\t$answers\t")
          pbAlignmentOpt.flatMap(_.alignedQAs(qa)) match {
            case None => sb.append("No PB alignment")
            case Some(PredArg(p, a)) =>
              sb.append(s"${p.head.token} (${p.head.index}) --" + a.label + "-> ")
              sb.append(Text.render(a.words.map(_.token)))
          }
          sb.append("\t")
          nbAlignment.alignedQAs(qa) match {
            case None => sb.append("No NB alignment")
            case Some(PredArg(p, a)) =>
              sb.append(s"${p.head.token} (${p.head.index}) --" + a.label + "-> ")
              sb.append(Text.render(a.words.map(_.token)))
          }
          sb.append("\n")
        }
      }

      if(pbAlignmentOpt.fold(false)(_.missedDeps.nonEmpty) || nbAlignment.missedDeps.nonEmpty || qasrlAlignment.missedDeps.nonEmpty) {
        sb.append("==\t" + Text.render(tokens) + "\n")
        pbAlignmentOpt.fold[Unit](sb.append("--\tNo PropBank data\n")) { pbAlignment =>
          sb.append("--\tPropBank dependencies missed:\n")
          pbAlignment.missedDeps.sortBy(_.pred.head.index).foreach(addPA)
        }
        sb.append("--\tNomBank dependencies missed:\n")
        nbAlignment.missedDeps.sortBy(_.pred.head.index).foreach(addPA)
        sb.append("--\tQA-SRL dependencies missed:\n")
        qasrlAlignment.missedDeps.sortBy(_.pred.head.index).foreach(addPA)
      }
    }
    val fileString = sb.toString
    // saveOutputFile(experimentName, "pbnb-missed-deps.tsv", fileString)
    annotationConfig.saveOutputFile("qasrl-missed-deps.tsv", fileString)
  }

}


object SRLComparison {

  case class PredArg(pred: Predicate, arg: ArgumentSpan)

  // the pred itself, discourse markers, negations, and auxiliaries we don't care about
  def labelIsIrrelevant(l: String) = {
    l == "V" || l.contains("DIS") || l.contains("NEG") || l.contains("MOD") ||
      l.contains("C-") || l.contains("R-") ||
      l == "rel"// || l == "Support"
  }

  def getRelevantPredArgs(pas: PredicateArgumentStructure) = pas.arguments
    .map(PredArg(pas.pred, _))
    .filterNot(pa => labelIsIrrelevant(pa.arg.label))
    .filterNot(pa => Inflections.auxiliaryVerbs.contains(pa.pred.head.token.lowerCase))
    .filterNot(pa => pa.arg.words.contains(pa.pred.head))

  case class PASAlignment(
    allPAs: List[PredArg],
    alignedQAs: Map[SourcedQA[SentenceId], Option[PredArg]],
    stats: PrecisionRecall) {
    def coveredDeps = alignedQAs.values.flatten.toSet
    def missedDeps = {
      val covered = coveredDeps
      allPAs.filterNot(covered)
    }
  }

  def sampleQAPairs(sqas: List[SourcedQA[SentenceId]], n: Int = 1) = {
    val qasByPrompt = sqas.groupBy(sqa => (sqa.id.sentenceId, sqa.id.keywords))
    val qas = qasByPrompt.values.flatMap { promptQAs =>
      val qasByAssignment = promptQAs.groupBy(sqa => (sqa.id.sentenceId, sqa.id.keywords, sqa.id.workerId))
      val sample = util.Random.shuffle(qasByAssignment.keys.toVector).take(n)
      for {
        assignmentId <- sample
        sqa <- qasByAssignment(assignmentId)
      } yield sqa
    }
    qas
  }

  val resourcePath = java.nio.file.Paths.get("datasets")

  lazy val PTB = new PTBFileSystemService(
    resourcePath.resolve("ptb")
  )

  lazy val PropBank = new PropBankFileSystemService(
    resourcePath.resolve("propbank")
  )

  lazy val NomBank = new NomBankFileSystemService(
    resourcePath.resolve("nombank.1.0"), PTB
  )

  lazy val QASRL = new QASRLFileSystemService(
    resourcePath.resolve("qasrl"), PTB
  )
}
