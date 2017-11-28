package qamr

import cats.implicits._

import akka.actor._
import akka.stream.scaladsl.{Flow, Source}

import com.amazonaws.services.mturk.model.QualificationRequirement
import com.amazonaws.services.mturk.model.QualificationTypeStatus
import com.amazonaws.services.mturk.model.Locale
import com.amazonaws.services.mturk.model.ListQualificationTypesRequest
import com.amazonaws.services.mturk.model.CreateQualificationTypeRequest

import nlpdata.util.Text
import nlpdata.util.HasTokens
import nlpdata.util.HasTokens.ops._

import spacro._
import spacro.tasks._
import spacro.util.RichAmazonMTurk

import qamr._
import qamr.util._

import upickle.default._

import scala.concurrent.duration._
import scala.language.postfixOps

import scala.collection.JavaConverters._

class AnnotationPipeline[SID : Reader : Writer : HasTokens](
  val allIds: Vector[SID], // IDs of sentences to annotate
  numGenerationAssignmentsForPrompt: GenerationPrompt[SID] => Int,
  annotationDataService: AnnotationDataService,
  isStopword: IsStopword,
  qualTest: QualTest,
  frozenGenerationHITTypeID: Option[String] = None,
  frozenValidationHITTypeID: Option[String] = None,
  generationAccuracyQualTypeLabel: Option[String] = None,
  validationAgreementQualTypeLabel: Option[String] = None,
  validationTestQualTypeLabel: Option[String] = None)(
  implicit config: TaskConfig) {

  implicit val ads = annotationDataService
  implicit val is = isStopword
  import QAMRSettings._

  implicit object SIDHasKeyIndices extends HasKeyIndices[SID] {
    override def getKeyIndices(id: SID): Set[Int] = id.tokens
      .zipWithIndex
      .collect {
      case (token, index) if !isStopword(token) => index
    }.toSet
  }

  lazy val allPrompts = allIds.flatMap { id =>
    val tokens = id.tokens
    val splits = tokenSplits(tokens)
    splits.map(GenerationPrompt[SID](id, _))
  }

  val taskPageHeadLinks = {
    import scalatags.Text.all._
    List(
      link(
        rel := "stylesheet",
        href := s"https://${config.serverDomain}:${config.httpsPort}/styles.css"),
      link(
        rel := "stylesheet",
        href := s"http://${config.serverDomain}:${config.httpPort}/styles.css")
    )
  }


  import config.hitDataService

  val approvalRateQualificationTypeID = "000000000000000000L0"
  val approvalRateRequirement = new QualificationRequirement()
    .withQualificationTypeId(approvalRateQualificationTypeID)
    .withComparator("GreaterThanOrEqualTo")
    .withIntegerValues(95)
    .withRequiredToPreview(false)

  val localeQualificationTypeID = "00000000000000000071"
  val localeRequirement = new QualificationRequirement()
    .withQualificationTypeId(localeQualificationTypeID)
    .withComparator("EqualTo")
    .withLocaleValues(new Locale().withCountry("US"))
    .withRequiredToPreview(false)

  val genAccQualTypeLabelString = generationAccuracyQualTypeLabel.fold("")(x => s"[$x] ")
  val genAccQualTypeName = s"${genAccQualTypeLabelString}Question-answer writing accuracy % (auto-granted)"
  val genAccQualType = config.service.listAllQualificationTypes(
    new ListQualificationTypesRequest()
      .withQuery(genAccQualTypeName)
      .withMustBeOwnedByCaller(true)
      .withMustBeRequestable(false)
  ).find(_.getName == genAccQualTypeName).getOrElse {
    System.out.println("Generating generation qualification type...")
    config.service.createQualificationType(
      new CreateQualificationTypeRequest()
        .withName(genAccQualTypeName)
        .withKeywords("language,english,question answering")
        .withDescription("""The rate at which questions provided for our
          question-answer generation task were judged
          valid and non-redundant, using the input of validators.""".replaceAll("\\s+", " "))
        .withQualificationTypeStatus(QualificationTypeStatus.Active)
        .withAutoGranted(true)
        .withAutoGrantedValue(101)
    ).getQualificationType
  }
  val genAccQualTypeId = genAccQualType.getQualificationTypeId
  val genAccuracyRequirement = new QualificationRequirement()
    .withQualificationTypeId(genAccQualTypeId)
    .withComparator("GreaterThanOrEqualTo")
    .withIntegerValues(math.round(generationAccuracyBlockingThreshold * 100.0).toInt)
    .withRequiredToPreview(false)

  val valAgrQualTypeLabelString = validationAgreementQualTypeLabel.fold("")(x => s"[$x] ")
  val valAgrQualTypeName = s"${valAgrQualTypeLabelString}Question answering agreement % (auto-granted)"
  val valAgrQualType = config.service.listAllQualificationTypes(
    new ListQualificationTypesRequest()
      .withQuery(valAgrQualTypeName)
      .withMustBeOwnedByCaller(true)
      .withMustBeRequestable(false)
  ).find(_.getName == valAgrQualTypeName).getOrElse {
    System.out.println("Generating validation qualification type...")
    config.service.createQualificationType(
      new CreateQualificationTypeRequest()
        .withName(valAgrQualTypeName)
        .withKeywords("language,english,question answering")
        .withDescription("""The rate at which answers and validity judgments
          in our question answering task agreed with other validators.""".replaceAll("\\s+", " "))
        .withQualificationTypeStatus(QualificationTypeStatus.Active)
        .withAutoGranted(true)
        .withAutoGrantedValue(101)
    ).getQualificationType
  }
  val valAgrQualTypeId = valAgrQualType.getQualificationTypeId
  val valAgreementRequirement = new QualificationRequirement()
    .withQualificationTypeId(valAgrQualTypeId)
    .withComparator("GreaterThanOrEqualTo")
    .withIntegerValues(math.round(validationAgreementBlockingThreshold * 100.0).toInt)
    .withRequiredToPreview(false)

  val valTestQualTypeLabelString = validationTestQualTypeLabel.fold("")(x => s"[$x] ")
  val valTestQualTypeName = if(config.isProduction) s"${valTestQualTypeLabelString}Question answering test score (%)"
                            else "Sandbox test score qual"
  val valTestQualType = config.service.listAllQualificationTypes(
    new ListQualificationTypesRequest()
      .withQuery(valTestQualTypeName)
      .withMustBeOwnedByCaller(true)
      .withMustBeRequestable(false)
  ).find(_.getName == valTestQualTypeName).getOrElse {
    System.out.println("Generating validation test qualification type...")
    config.service.createQualificationType(
      new CreateQualificationTypeRequest()
        .withName(valTestQualTypeName)
        .withKeywords("language,english,question answering")
        .withDescription("""Score on the qualification test for the question answering task,
          as a test of your understanding of the instructions.""".replaceAll("\\s+", " "))
        .withQualificationTypeStatus(QualificationTypeStatus.Active)
        .withRetryDelayInSeconds(300L)
        .withTest(qualTest.testString)
        .withAnswerKey(qualTest.answerKeyString)
        .withTestDurationInSeconds(1200L)
        .withAutoGranted(false)
    ).getQualificationType
  }
  val valTestQualTypeId = valTestQualType.getQualificationTypeId
  val valTestRequirement = new QualificationRequirement()
    .withQualificationTypeId(valTestQualTypeId)
    .withComparator("GreaterThanOrEqualTo")
    .withIntegerValues(75)
    .withRequiredToPreview(false)

  val genHITType = HITType(
    title = s"Write question-answer pairs about a sentence's meaning",
    description = s"""
      Given a sentence and some words from that sentence,
      write questions and answers involving each word.
      Write more question-answer pairs for increasing bonuses, and
      maintain high accuracy to stay qualified.
    """.trim.replace("\\s+", " "),
    reward = generationReward,
    keywords = "language,english,question answering",
    qualRequirements = Array[QualificationRequirement](
      approvalRateRequirement, localeRequirement, genAccuracyRequirement
    ))

  lazy val genApiFlow = Flow[GenerationApiRequest[SID]].map {
    case GenerationApiRequest(id) =>
      GenerationApiResponse(id.tokens)
  }

  lazy val genTaskSpec = TaskSpecification.NoAjax[GenerationPrompt[SID], List[WordedQAPair], GenerationApiRequest[SID], GenerationApiResponse](
    generationTaskKey, genHITType, genApiFlow, allPrompts,
    taskPageHeadElements = taskPageHeadLinks,
    frozenHITTypeId = frozenGenerationHITTypeID)

  // validation task definition

  val valHITType = HITType(
    title = s"Answer simple questions about a sentence",
    description = s"""
      Given a sentence and several questions about it,
      highlight the part of the sentence that answers each question,
      and mark questions that are invalid or redundant.
      Maintain high agreement with others to stay qualified.
    """.trim,
    reward = validationReward,
    keywords = "language,english,question answering",
    qualRequirements = Array[QualificationRequirement](
      approvalRateRequirement, localeRequirement, valAgreementRequirement, valTestRequirement
    ))

  lazy val valApiFlow = Flow[ValidationApiRequest[SID]].map {
    case ValidationApiRequest(id) =>
      ValidationApiResponse(id.tokens)
  }

  lazy val sampleValPrompt = ValidationPrompt[SID](
    allPrompts.head, "", "",
    List(WordedQAPair(0, "Who is awesome?", Set(1, 2, 3, 4)),
         WordedQAPair(1, "What did Julian do?", Set(5, 6, 8, 9)),
         WordedQAPair(1, "What did Julian do?", Set(5, 6, 8, 9)),
         WordedQAPair(1, "What did Julian do?", Set(5, 6, 8, 9))))

  lazy val valTaskSpec = TaskSpecification.NoAjax[ValidationPrompt[SID], List[ValidationAnswer], ValidationApiRequest[SID], ValidationApiResponse](
    validationTaskKey, valHITType, valApiFlow, Vector(sampleValPrompt),
    taskPageHeadElements = taskPageHeadLinks,
    frozenHITTypeId = frozenValidationHITTypeID)

  // hit management --- circularly defined so they can communicate

  import config.actorSystem

  var sentenceTrackerPeek: SentenceTracker[SID] = null

  lazy val sentenceTracker: ActorRef = actorSystem.actorOf(
    Props {
      sentenceTrackerPeek = new SentenceTracker[SID](genTaskSpec.hitTypeId, valTaskSpec.hitTypeId)
      sentenceTrackerPeek
    })

  var genManagerPeek: GenerationHITManager[SID] = null
  var valManagerPeek: ValidationHITManager[SID] = null

  lazy val genHelper = new HITManager.Helper(genTaskSpec)
  lazy val genManager: ActorRef = if(config.isProduction) {
    actorSystem.actorOf(
      Props {
        genManagerPeek = new GenerationHITManager(
          genHelper,
          genAccQualTypeId,
          valHelper,
          valManager,
          sentenceTracker,
          numGenerationAssignmentsForPrompt, 30, allPrompts.iterator)
        genManagerPeek
      })
  } else {
    actorSystem.actorOf(
      Props {
        genManagerPeek = new GenerationHITManager(
          genHelper,
          genAccQualTypeId,
          valHelper,
          valManager,
          sentenceTracker,
          _ => 1, 3, allPrompts.iterator)
        genManagerPeek
      })
  }

  lazy val valHelper = new HITManager.Helper(valTaskSpec)
  lazy val valManager: ActorRef = if(config.isProduction) {
    actorSystem.actorOf(
      Props {
        valManagerPeek = ValidationHITManager(
          valHelper,
          valAgrQualTypeId,
          genManager,
          sentenceTracker,
          _ => 2, 50)
        valManagerPeek
      })
  } else {
    actorSystem.actorOf(
      Props {
        valManagerPeek = ValidationHITManager(
          valHelper,
          valAgrQualTypeId,
          genManager,
          sentenceTracker,
          _ => 1, 3)
        valManagerPeek
      })
  }

  val dashboardApiFlow = Flow[Unit]
    .merge(Source.tick(initialDelay = 0.seconds, interval = 1.minute, ()))
    .filter(_ => genManagerPeek != null && valManagerPeek != null && sentenceTrackerPeek != null)
    .map { _ =>
    val last5Sentences = sentenceTrackerPeek.finishedSentenceStats.take(5).flatMap { stats =>
      val sentence = stats.id.tokens
      scala.util.Try(
        stats -> SentenceHITInfo(
          sentence,
          stats.genHITIds.toList
            .map(hitDataService.getHITInfo[GenerationPrompt[SID], List[WordedQAPair]](genTaskSpec.hitTypeId, _))
            .map(_.get),
          stats.valHITIds.toList
            .map(hitDataService.getHITInfo[ValidationPrompt[SID], List[ValidationAnswer]](valTaskSpec.hitTypeId, _))
            .map(_.get))
      ).toOption
    }.toMap
    SummaryInfo(
      // generation
      numGenActive = genHelper.numActiveHITs,
      genWorkerStats = genManagerPeek.allWorkerStats,
      genFeedback = genManagerPeek.feedbacks.take(20),
      // validation
      numValPromptsWaiting = valManagerPeek.queuedPrompts.numManuallyEnqueued,
      numValActive = valHelper.numActiveHITs,
      valWorkerInfo = valManagerPeek.allWorkerInfo,
      valFeedback = valManagerPeek.feedbacks.take(20),
      // final results
      lastFewSentences = last5Sentences,
      aggSentenceStats = sentenceTrackerPeek.aggregateSentenceStats)
  }

  lazy val dashboardTaskSpec = TaskSpecification.NoAjax[Unit, Unit, Unit, SummaryInfo[SID]](
    dashboardTaskKey, null, dashboardApiFlow, Vector(()),
    frozenHITTypeId = null)

  lazy val server = new Server(List(genTaskSpec, valTaskSpec, dashboardTaskSpec))
  lazy val genActor = actorSystem.actorOf(Props(new TaskManager(genHelper, genManager)))
  lazy val valActor = actorSystem.actorOf(Props(new TaskManager(valHelper, valManager)))

  // used to schedule data-saves
  private[this] var schedule: List[Cancellable] = Nil
  def startSaves(interval: FiniteDuration = 5 minutes): Unit = {
    if(schedule.exists(_.isCancelled) || schedule.isEmpty) {
      schedule = List(genManager, valManager, sentenceTracker).map(actor =>
        config.actorSystem.scheduler.schedule(
          2 seconds, interval, actor, SaveData)(
          config.actorSystem.dispatcher, actor)
      )
    }
  }
  def stopSaves = schedule.foreach(_.cancel())
  def saveData = {
    genManager ! SaveData
    valManager ! SaveData
    sentenceTracker ! SaveData
  }

  def setGenHITsActive(n: Int) =
    genManager ! SetNumHITsActive(n)
  def setValHITsActive(n: Int) =
    valManager ! SetNumHITsActive(n)

  import TaskManager.Message._
  def start(interval: FiniteDuration = 30 seconds) = {
    server
    startSaves()
    genActor ! Start(interval, delay = 0 seconds)
    valActor ! Start(interval, delay = 3 seconds)
  }
  def stop() = {
    genActor ! Stop
    valActor ! Stop
    stopSaves
  }
  def delete() = {
    genActor ! Delete
    valActor ! Delete
  }
  def expire() = {
    genActor ! Expire
    valActor ! Expire
  }
  def update() = {
    server
    genActor ! Update
    valActor ! Update
  }
  def save() = {
    sentenceTracker ! SaveData
    genManager ! SaveData
    valManager ! SaveData
  }

  // for use while it's running. Ideally instead of having to futz around at the console calling these functions,
  // in the future you could have a nice dashboard UI that will help you examine common sources of issues

  def allGenInfos = hitDataService.getAllHITInfo[GenerationPrompt[SID], List[WordedQAPair]](genTaskSpec.hitTypeId).get
  def allValInfos = hitDataService.getAllHITInfo[ValidationPrompt[SID], List[ValidationAnswer]](valTaskSpec.hitTypeId).get

  def workerGenInfos(workerId: String) = for {
    hi <- allGenInfos
    assignment <- hi.assignments
    if assignment.workerId == workerId
  } yield HITInfo(hi.hit, List(assignment))

  // sorted increasing by number of disagreements
  def workerValInfos(workerId: String) = {
    val scored = for {
      hi <- allValInfos
      if hi.assignments.exists(_.workerId == workerId)
      workerAssignment = hi.assignments.find(_.workerId == workerId).get
      nonWorkerAssignments = hi.assignments.filter(_.workerId != workerId)
      avgNumDisagreed = hi.hit.prompt.qaPairs.size - nonWorkerAssignments.map(a => ValidationAnswer.numAgreed(workerAssignment.response, a.response)).meanOpt.get
    } yield (HITInfo(hi.hit, workerAssignment :: nonWorkerAssignments), avgNumDisagreed)
    scored.sortBy(_._2).map(_._1)
  }

  def currentGenSentences: List[(SID, String)] = {
    genHelper.activeHITInfosByPromptIterator.map(_._1.id).map(id =>
      id -> Text.render(id.tokens)
    ).toList
  }

  def renderValidation(info: HITInfo[ValidationPrompt[SID], List[ValidationAnswer]]) = {
    val sentence = info.hit.prompt.genPrompt.id.tokens
    info.assignments.map { assignment =>
      Text.render(sentence) + "\n" +
        info.hit.prompt.qaPairs.zip(assignment.response).map {
          case (WordedQAPair(kwIndex, question, answerIndices), valAnswer) =>
            val answerString = Text.renderSpan(sentence, answerIndices)
            val validationString = ValidationAnswer.render(sentence, valAnswer, info.hit.prompt.qaPairs)
            s"\t$question --> $answerString \t|$validationString"
        }.mkString("\n")
    }.mkString("\n") + "\n"
  }

  def allSentenceStats: Map[SID, SentenceStats[SID]] = {
    val genInfosById = allGenInfos.groupBy(_.hit.prompt.id).withDefaultValue(Nil)
    val valInfosById = allValInfos.groupBy(_.hit.prompt.genPrompt.id).withDefaultValue(Nil)
    allIds.map { id =>
      val afterGen = genInfosById(id)
        .map(_.hit.prompt.keywords.toSet)
        .foldLeft(emptyStatus(id))(_ withKeywords _)
      val valStart = valInfosById(id)
        .map(_.hit.prompt)
        .foldLeft(afterGen)(_ beginValidation _)
      val valFinish = valInfosById(id)
        .foldLeft(valStart) {
        case (status, hitInfo) => status.finishValidation(hitInfo.hit.prompt, hitInfo.assignments)
      }
      id -> SentenceTracker.makeStats(valFinish, genTaskSpec.hitTypeId, valTaskSpec.hitTypeId)
    }.toMap
  }

  def aggSentenceStats: AggregateSentenceStats = AggregateSentenceStats.aggregate(
    allSentenceStats.values.toList
  )
}
