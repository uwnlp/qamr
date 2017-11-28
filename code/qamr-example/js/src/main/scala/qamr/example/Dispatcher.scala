package qamr.example

import qamr.QAMRSettings
import qamr.QAMRDispatcher
import qamr.util.Styles
import qamr.util.dollarsToCents

import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react._

import scalacss.ScalaCssReact._

import scalajs.js.JSApp

object Dispatcher extends QAMRDispatcher[SentenceId] with JSApp {

  import QAMRSettings._

  override val requireWhAtQuestionBeginning: Boolean = false

  def generationExample(question: String, answer: String, isGood: Boolean, tooltip: String) =
    <.li(
      <.div(
        if(isGood) Styles.goodGreen else Styles.badRed,
        ^.className := "tooltip",
        <.span(question),
        <.span(" --> "),
        <.span(answer),
        <.span(^.className := "tooltiptext", tooltip)
      )
    )

  override val generationInstructions = <.div(
    <.h2("""Task Summary"""),
    <.p(<.span("""This task is for an academic research project by the natural language processing group at the University of Washington.
        We wish to deconstruct the meanings of English sentences into lists of questions and answers.
        You will be presented with a selection of English text with a set of """), <.b("special words"), " written in bold."),
    <.p("""For each special word, you will write questions and their answers, where the answer is taken from the sentence and
           either """, <.b("""the question or the answer """),
        """contains the special word. """,
        <.b("""You will earn bonuses by writing more questions and answers. """),
        """For example, consider the sentence:"""),
    <.blockquote(<.i("The jubilant ", <.span(Styles.specialWord, "protesters"),
                     " celebrated after executive intervention canceled the project.")),
    <.p("""Valid question-answer pairs include:"""),
    <.ul(
      <.li("How did the ", <.b("protesters "), "feel? --> jubilant"),
      <.li("Who celebrated? --> the ", <.b("protesters"))),
    <.p(<.b("""Warning: """), """The text shown to you is drawn randomly
           from Wikipedia and news articles from the past few years.
           We have no control over the contents of the text, which may discuss sensitive subjects,
           including crime and death, or occasionally contain offensive ideas. Please use appropriate discretion.
           (If you receive a selection of text that is not in English, please skip or return the HIT and let us know.)"""),
    <.h2("""Requirements"""),
    <.p("""This task is best fit for native speakers of English.
        Your response must be grammatical, fluent English that satisfies the following criteria:"""),
    <.ol(
      <.li("""Either the question or the answer contains the special word."""),
      <.li("""The question contains at least one word from the sentence."""),
      <.li("The question is about the meaning of the sentence (and not, for example, the order of the words)."),
      <.li("""The question is answered obviously and explicitly in the sentence."""),
      <.li("""The question is open-ended: yes/no and either/or questions are not allowed."""),
      <.li("""None of your question-answer pairs are redundant with each other,
              even for different special words.""")
    ),
    <.p("""See the examples for further explanation."""),
    <.h2("""Examples"""),
    <.p("Suppose you are given the following sentence:"),
    <.blockquote(<.i(""" In the year since the regulations were enacted,
                         the Director of the Environmental Protection Agency (EPA),
                         Gina McCarthy, has been aggressive in enforcing them.""")),
    <.p("""Here are questions and answers that someone may write
        (ignoring the special word requirement for now).
        Good ones are green while examples of bad questions are in red.
        Mouse over each example to see an explanation."""),
    <.ul(
      generationExample(question = "What was enacted?", answer = "the regulations", isGood = true,
              tooltip = """This is a standard, straightforward question that is answered literally by the sentence.
                           Most questions should look something like this."""),
      generationExample(question = "In the what since?", answer = "year", isGood = false,
              tooltip = """This simply replaces a word with "what"
                           instead of using it to form a proper English question."""),
      generationExample(question = "How long was it since the regulations were enacted?", answer = "the year", isGood = true,
              tooltip = """While "a year" is a more natural answer, "the year" is the closest you can get
                           and the question is answered in the sentence so it is still acceptable."""),
      generationExample(question = "What does EPA stand for?", answer = "Environmental Protection Agency", isGood = true,
              tooltip = """Asking about the meanings of words or acronyms, when they are explicitly defined
                           in the sentence, is acceptable."""),
      generationExample(question = "What pronoun refers to the regulations?", answer = "them", isGood = false,
              tooltip = """This question is about the words in the sentence instead of the sentence's meaning,
                           so it is unacceptable."""),
      generationExample(question = "Who enacted the regulations?", answer = "the Environmental Protection Agency (EPA)", isGood = false,
              tooltip = """This is not directly implied by the sentence, so the question is invalid.
                           (In fact, it is also wrong: it is Congress which enacts regulations, not the EPA.)"""),
      generationExample(question = "What is Gina's last name?", answer = "McCarthy", isGood = true,
              tooltip = """This is an acceptable question much like "What does EPA stand for?",
                           but note that the similar question "What is the last word in Gina's name? would be invalid."""),
      generationExample(question = "What is the is the Agency responsible for?", answer = "Environmental Protection", isGood = true,
              tooltip = """While "responsibility" is not explicitly mentioned in the sentence,
                           this fact is part of the meaning of the name "Environmental Protection Agency"."""),
      generationExample(question = "Was McCarthy aggressive or lax?", answer = "aggressive", isGood = false,
              tooltip = """This is an either/or question, which is disallowed."""),
      generationExample(question = "What was enforced?", answer = "them", isGood = true,
              tooltip = """The answer "the regulations" is also acceptable here. It is okay for the answer
                           to be non-unique if it is because multiple different phrases
                           refer to the same thing.""")
    ),
    <.p("Now consider the following sentence, with the special word ", <.b("decision. ")),
    <.blockquote(<.i("""I take full and complete responsibility for my thoughtless """, <.span(Styles.specialWord, """decision"""),
                     """ to disclose these materials to the public. """)),
    <.p("Here are examples of some good question-answer pairs:"),
    <.ul(
      <.li(<.div(Styles.goodGreen, ^.className := "tooltip",
                 <.span("Who "), <.b("decided "), <.span("something? --> I"),
                 <.span(^.className := "tooltiptext",
                        """Where possible, change nouns like "decision" to verbs in order to write short questions about them."""))),
      <.li(<.div(Styles.goodGreen, ^.className := "tooltip",
                 <.span("What kind of "), <.b("decision"), <.span("? --> thoughtless"),
                 <.span(^.className := "tooltiptext",
                        """To get descriptive words as answers, you may need to ask "What kind" or similar questions.""")))
    ),
    <.p("""Now suppose you are given the following sentence, with the special word """, <.b("pushed. ")),
    <.blockquote(<.i("""Alex """, <.span(Styles.specialWord, "pushed"), """ Chandler at school today.""")),
    <.p("Mouse over the following examples of bad question-answer pairs for explanations:"),
    <.ul(
      <.li(<.div(Styles.badRed, ^.className := "tooltip",
                 <.span("Who got hurt? --> Chandler"),
                 <.span(^.className := "tooltiptext",
                        """The question must include some content word from the sentence, which this fails to do."""))),
      <.li(<.div(Styles.badRed, ^.className := "tooltip",
                 <.span("Did Alex or Chandler ", <.b("push"), " someone? --> Alex"),
                 <.span(^.className := "tooltiptext",
                        """Either/or and yes/no questions are not allowed."""))),
      <.li(<.div(Styles.badRed, ^.className := "tooltip",
                 <.span("Where did Alex ", <.b("push"), " Chandler? --> at school today"),
                 <.span(^.className := "tooltiptext",
                        """The question asked "where", so including the word "today" is incorrect.""")))
    ),
    <.h2("Redundancy"),
    <.p(""" Two question-answer pairs are """, <.b("redundant "), """if they are both """,
        <.b("asking the same question "), "and they ", <.b("have the same answer. "), """
        None of your question-answer pairs in one HIT should be redundant with each other.
        For example, consider the following sentence and questions:"""),
    <.blockquote(<.i("""Intelligence documents leaked to the public today have dealt another blow to the agency's credibility.""")),
    <.ul(
      <.li(<.div("When was something leaked?")),
      <.li(<.div("On what day was something leaked?"))
    ),
    <.p("""They have the same answer (""", <.i("today"), """) and the second question is just a minor rephrasing of the first, so """,
        <.b(Styles.badRed, "these are redundant. "), """
        However, consider the following:"""),
    <.ul(
      <.li(<.div("What was leaked today?")),
      <.li(<.div("What kind of documents?"))
    ),
    <.p("""While these both may be answered with the same phrase, """, <.i("intelligence documents"), """,
        these questions are """, <.b(Styles.goodGreen, "not redundant "), """ because they are asking about different things:
        the first is asking about what it is that leaked,
        and the second is asking about a characteristic of the documents."""),
    <.h2("""Conditions & Bonuses"""),
    <.p(s"""For each HIT, you will be shown up to four special words from the sentence.
          You are required to write at least one question-answer pair for each special word.
          However, you will receive bonuses if you come up with more.
          (As you complete each one, new fields will appear for you to write more.)
          The bonus per question increases by ${dollarsToCents(bonusIncrement)}c for each one you write;
          your reward will be greatest if you can present """,
          <.b("the complete set of possible questions and answers "),
          """that relate the special words to each other and the rest of the sentence.
          On average, it should take less than 30 seconds per question-answer pair.
          """),
    <.p("""Your work will be evaluated by other workers according to the above criteria. """,
          <.b("""You will only be awarded bonuses for your good, non-redundant question-answer pairs, """),
          s""" as judged by other workers.
          This means the "total potential bonus" indicator is just an upper bound on what you may receive,
          which will depend on the quality of your responses.
          Your bonus will be awarded as soon as validators have checked all of your question-answer pairs,
          which will happen shortly after you submit (but will vary depending on worker availability).
          Your accuracy qualification value for this HIT will be updated to match your current accuracy
          as your questions are validated.
          If this number drops below ${(100 * generationAccuracyBlockingThreshold).toInt},
          you will no longer qualify for the task.
          There is a grace period of several HITs before your score is allowed to drop too low;
          if your score is exactly ${(100 * generationAccuracyBlockingThreshold).toInt}
          it may be that your real accuracy is lower but you are in the grace period.
          The first time your score gets near or below the threshold, you will be sent a notification,
          but you can check it at any time in your qualifications.
          (Note, however, that the validators will sometimes make mistakes,
          so there is an element of randomness to it: don't read too deeply into small changes in your accuracy.)"""),
    <.h2("""Tips"""),
    <.p(s"""To make the task go quickly, make your questions as short and simple as possible.
            (There is a ${questionCharLimit}-character limit, which will be indicated in red when you approach it.)
            Feel free to use generic words like "someone" and "something" to do so."""),
    <.p(""" You will find that the vast majority of your questions begin with """,
        <.b("Who, what, when, where, why, whose, which, "),
        " or ",
        <.b("how"),
        """. There is also variety of possible """,
        <.i(" what"), ", ", <.i(" which"), ", and ", <.i(" how "), """ questions you may ask,
        which start with phrases like """,
        <.b(" What color, what day, which country, which person, how much, how long, how often, how large, "),
        """ and many others. If you're having trouble coming up with questions using these words,
        remember that you can use the special word in """, <.b(" either the question or the answer, "),
        """ and when using it in the question, you can change its form,
        like turning "decision" into "decide", or expanding symbols to their English versions
        (like $ as dollars, or ° as degrees). """),
    <.p("""Finally, it's to your advantage to """,
        <.b("write as many good questions as possible. "),
        """ If you can come up with more questions that you're sure are valid in one HIT,
            it will help keep your accuracy high in case you have trouble writing the required questions in other HITs.
            In addition, the bonuses increase the more questions you write,
            so this will help maximize your earnings per question."""),
    <.p("""If you have any questions, concerns, or points of confusion,
        please share them in the "Feedback" field.""")
    )

  def validationExample(question: String, answer: String, isGood: Boolean, tooltip: String) =
    <.li(
      <.div(
        ^.className := "tooltip",
        <.span(question),
        <.span(" --> "),
        <.span(
          if(isGood) Styles.goodGreen else Styles.badRed,
          answer),
        <.span(^.className := "tooltiptext", tooltip)
      )
    )


  override val validationInstructions = <.div(
    <.h2("""Task Summary"""),
    <.p(s"""This task is for an academic research project by the natural language processing group at the University of Washington.
           We wish to deconstruct the meanings of English sentences into lists of questions and answers.
           You will be presented with a selection of English text and a list of questions (usually at least four)
           prepared by other annotators."""),
    <.p("""You will highlight the words in the sentence that correctly answer the question,
           as well as mark whether questions are invalid or redundant.
           For example, for the following sentence and questions, you should respond with the answers in green:"""),
    <.blockquote(<.i("The jubilant protesters celebrated after executive intervention canceled the project.")),
    <.ul(
      <.li("How did the protesters feel? --> ", <.span(Styles.goodGreen, "jubilant")),
      <.li("When did someone celebrate? --> ", <.span(Styles.goodGreen, "after executive intervention canceled the project")),
      <.li("Who celebrated? --> ", <.span(Styles.goodGreen, "The jubilant protesters"))),
    <.p(s"""You will be paid in accordance with the number of questions shown to you, with a bonus of
            ${dollarsToCents(validationBonusPerQuestion)}c per question after the first four
            that will be paid when the assignment is approved."""),
    <.p(<.b("""Warning: """), """The text shown to you is drawn randomly
           from Wikipedia and news articles from the past few years.
           We have no control over the contents of the text, which may discuss sensitive subjects,
           including crime and death, or occasionally contain offensive ideas. Please use appropriate discretion."""),
    <.h2("""Requirements"""),
    <.p("""This task is best fit for native speakers of English.
        For each question, you will either """,
        <.b("answer it, "), "mark it ", <.b("invalid, "), "or mark it ", <.b("redundant.")),
    <.h3("Answers"),
    <.p("""Each answer must be """, <.b("correct "), "and ", <.b("as grammatical as possible"),
        """. Include only the words relevant for answering the question,
        but if all else is equal, prefer longer answers over shorter ones.
        If there are multiple correct answers written as a list or with an "and", you should choose the whole list."""),
    <.p("""In long sentences, an object may be mentioned multiple times, or a phrase may appear in the sentence multiple times.
           In cases such as this where there are multiple possible correct answers,
           you should choose the phrase that most naturally answers the question in your opinion.
           This may be the best phrase for describing the answer,
           or it might be the closest one to the content that the question is asking about.
           (Since this is a small minority of cases, disagreements on these
           should not significantly hurt your agreement numbers.)"""),
    <.h3("Invalid Questions"),
    <.p("""A question should be marked invalid if any of the following are true:"""),
    <.ul(
      <.li("""It isn't about the meaning of the sentence (for example, asking "Which word comes after...")."""),
      <.li("It is not fluent English or has grammatical or spelling errors."),
      <.li("It is not obviously answered by what is expressed in the sentence."),
      <.li("""It does not contain any words from the sentence (for example, "What happened?" is usually invalid). Changing the forms of words (like changing "decision" to "decide") and expanding symbols (like writing $ as dollars or ° as degrees) is fine."""),
      <.li("It is a yes/no or either/or question, or other non-open-ended question.")
    ),
    <.p("""It is okay for a question not to be a full sentence, as long as it makes sense and it is grammatical English.
           For example, the question """, <.span(Styles.goodGreen, "Whose decision?"), """ would be fine if the phrase
           "my decision" appeared in the sentence. """,
        """Note that such short questions might lack the context we normally provide in conversational speech,
           but this does not make them invalid.
           Be sure to read the entire sentence to figure out what the question writer is asking about."""),
    <.p("""Questions might include the question word like "what" in the middle somewhere,
           as in """, <.span(Styles.goodGreen, "Protesters celebrated after what form of intervention?"), """ This is fine, but
           if the question is excessively unnatural, like """, <.span(Styles.badRed, "The what protesters?"), """
           or if it lacks a question word altogether and simply copies a phrase from the sentence
           (for example, """, <.span(Styles.badRed, "The protesters celebrated after?"), """) then it should be counted invalid.
        """),
    <.p("""If a question betrays a clear misunderstanding of the task or is clearly not written by a native English speaker,
           it should be counted invalid. You should forgive minor spelling errors (e.g., who's/whose, it's/its)
           as long as they do not change the meaning of the question."""),
    <.p("""If a question is both invalid and redundant, please mark it invalid."""),
    <.h3("Redundancy"),
    <.p(""" Two questions are """, <.b("redundant "), """if they """,
        <.b("have the same meaning "), "and they ", <.b("have the same answer. "), """
        For example, suppose you are given the following sentence and questions:"""),
    <.blockquote(<.i("""Intelligence documents leaked to the public today have dealt another blow to the agency's credibility.""")),
    <.ul(
      <.li(<.div("When was something leaked?")),
      <.li(<.div("On what day was something leaked?"))
    ),
    <.p("""They have the same answer (""", <.i("today"), """) and the second question is just a minor rephrasing of the first, so """,
        <.b(Styles.badRed, "these are redundant. "), """
        However, consider the following:"""),
    <.ul(
      <.li(<.div("What was leaked today?")),
      <.li(<.div("What kind of documents?"))
    ),
    <.p("""While these both may be answered with the same phrase, """, <.i("intelligence documents"), """,
        these questions are """, <.b(Styles.goodGreen, "not redundant "), """ because they are asking about different things:
        the first is asking about what it is that leaked,
        and the second is asking about a characteristic of the documents."""),
    <.p(""" You may also come upon two questions ask about essentially the same thing,
        but where the order of question and answer is reversed. In these cases, the two are """, <.b(" not "),
        """ redundant, since they have different answers. """),
    <.h2("""Examples"""),
    <.p("Suppose you are given the following sentence:"),
    <.blockquote(<.i(""" In the year since the regulations were enacted,
                         the Director of the Environmental Protection Agency (EPA),
                         Gina McCarthy, has been aggressive in enforcing them.""")),
    <.p("""Here are examples of questions others might write, and how you should answer them.
           Mouse over each for an explanation."""),
    <.ul(
      validationExample(question = "What was enacted?", answer = "the regulations", isGood = true,
              tooltip = """This is a standard, straightforward question that is answered literally by the sentence.
                           Most questions should look something like this."""),
      validationExample(question = "In the what since?", answer = "<Invalid>", isGood = false,
              tooltip = """The question writer simply replaced a word with "what"
                           instead of using it to form a proper English question."""),
      validationExample(question = "How long was it since the regulations were enacted?", answer = "the year", isGood = true,
              tooltip = """While "a year" is a more natural answer, "the year" is the closest you can get
                           and the question is answered in the sentence so it is still acceptable."""),
      validationExample(question = "What does EPA stand for?", answer = "Environmental Protection Agency", isGood = true,
              tooltip = """Asking about the meanings of words or acronyms, when they are explicitly defined
                           in the sentence, is acceptable."""),
      validationExample(question = "What pronoun refers to the regulations?", answer = "<Invalid>", isGood = false,
              tooltip = """The question writer may have had the word "them" in mind, but this question
                           is about the words in the sentence instead of the sentence's meaning,
                           so it is unacceptable."""),
      validationExample(question = "Who enacted the regulations?", answer = "<Invalid>", isGood = false,
              tooltip = """The question writer may have been thinking it was the EPA, but that is not
                           actually expressed in the sentence, so the question is invalid.
                           (In fact, they were also wrong: it is Congress which enacts regulations, and not the EPA.)"""),
      validationExample(question = "What is Gina's last name?", answer = "McCarthy", isGood = true,
              tooltip = """This is an acceptable question much like "What does EPA stand for?",
                           but note that the similar question "What is the last word in Gina's name? would be invalid."""),
      validationExample(question = "What is the is the Agency responsible for?", answer = "Environmental Protection", isGood = true,
              tooltip = """While "responsibility" is not explicitly mentioned in the sentence,
                           this fact is part of the meaning of the name "Environmental Protection Agency".
                           Breaking down the meanings of names and descriptors like this is fine."""),
      validationExample(question = "Was McCarthy aggressive or lax?", answer = "<Invalid>", isGood = false,
              tooltip = """This is an either/or question, which is disallowed.""")
    ),
    <.p("Now suppose you are given the following sentence:"),
    <.blockquote(<.i("""I take full and complete responsibility for
                        my decision to disclose these materials to the public.""")),
    <.p("""Here are some more examples:"""),
    <.ul(
      validationExample(question = "Who decided to disclose something?", answer = "I", isGood = true,
              tooltip = """You can use pronouns like "I" and "it" to answer questions as long as they refer to the correct answer."""),
      validationExample(question = "What is someone responsible for?", answer = "my decision to disclose these materials to the public", isGood = true,
              tooltip = """If shorter and longer answers are equally correct, favor the longer one.
                           Provide this answer instead of just "my decision"."""),
      validationExample(question = "Who made the decision?",
              answer = """<Redundant with "Who decided to disclose something?">""", isGood = false,
              tooltip = """The question has the same meaning as asking who "decided" to do it,
                           as in the first question - and the answer is the same,
                           so this question is redundant."""),
      validationExample(question = "Who disclosed the materials?",
              answer = "I",
              isGood = true,
              tooltip = """This is not redundant with the first question, because it is asking about who did the disclosing
                           rather than who did the deciding."""),
      validationExample(question = "What did someone leak?",
              answer = "<Invalid>",
              isGood = false,
              tooltip = """This question does not contain any content words from the sentence.""")
    ),
    <.h2("Interface Controls"),
    <.ul(
      <.li("Change questions using the arrow keys."),
      <.li("Highlight your answer in the sentence."),
      <.li("""To mark the selected question as redundant, just click the question it's redundant with (which will turn orange).
              To unmark it, click the orange question again."""),
      <.li("""You can only mark a question as redundant with a question that has an answer.
              (If more than two questions are mutually redundant, answer one of them and mark the others as redundant with that one.)""")
    ),
    <.h2("Conditions and payment"),
    <.p(s"""You will be paid a bonus of ${dollarsToCents(validationBonusPerQuestion)}c
        for every question beyond $validationBonusThreshold.
        Your judgments will be cross-checked with other workers,
        and your agreement qualification value for this HIT will be updated to match your total agreement rate.
        If this number drops below ${(100 * validationAgreementBlockingThreshold).toInt}
        you will no longer qualify for the task.
        There is a grace period of several HITs before your score is allowed to drop too low;
        if your score is exactly ${(100 * validationAgreementBlockingThreshold).toInt}
        it may be that your real agreement rate is lower but you are in the grace period.
        The first time your score gets near or below the threshold, you will be sent a notification,
        but you can check it at any time in your qualifications.
        (Note, however, that other validators will sometimes make mistakes,
        so there is an element of randomness to it: don't read too deeply into small changes in your agreement rate.)
        As long as you are qualified, your work will be approved and the bonus will be paid within an hour."""),
    <.p("""If you have any questions, concerns, or points of confusion,
        please share them in the "Feedback" field.""")
  )
}
