package qamr.example

import qamr.QualTest

object ExampleQualTest extends QualTest {
  override val testString = s"""<?xml version="1.0" encoding="UTF-8"?>
<QuestionForm xmlns="http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2005-10-01/QuestionForm.xsd">
<Overview>
<Title>Answering simple questions about a sentence</Title>
<Text>Please carefully read over the instructions for our task named "Answer simple questions about a sentence". This qualification test will evaluate your understanding of those instructions, focusing on some of the trickier cases. It is very important for us that you follow the guidelines because you will be helping us detect question writers who do not correctly understand the task. The guidelines are also important because everyone has slightly different intuitions about how to answer these questions, but we need everyone to perform consistently so that you can accurately be judged by how well you agree with each other.
</Text>
<Text>It's a good idea have a tab open with the task preview so you can consult the instructions during this test. Feel free to take it as many times as necessary; your score can be a good form of feedback on how well you understand the expectations.</Text>
<Text>Suppose you get a HIT with the following sentence and list of questions. Please provide a judgment for each:</Text>
<Text>"According to the Nonexistent Centre for Imperialism Studies, exploitation colonialism involves fewer colonists and focuses on access to resources for export, typically to the metropole." </Text>
</Overview>

<Question>
  <QuestionIdentifier>q1</QuestionIdentifier>
  <IsRequired>true</IsRequired>
  <QuestionContent><Text>1. Exploitation colonialism focuses on?</Text></QuestionContent>
  <AnswerSpecification>
    <SelectionAnswer>
      <StyleSuggestion>radiobutton</StyleSuggestion>
      <Selections>

        <Selection>
        <SelectionIdentifier>q1-a1</SelectionIdentifier>
        <Text>access to resources for export, typically to the metropole</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q1-invalid</SelectionIdentifier>
        <Text>N/A: Invalid question</Text>
        </Selection>

      </Selections>
    </SelectionAnswer>
  </AnswerSpecification>
</Question>

<Question>
  <QuestionIdentifier>q2</QuestionIdentifier>
  <IsRequired>true</IsRequired>
  <QuestionContent><Text>2. What involves fewer colonists?</Text></QuestionContent>
  <AnswerSpecification>
    <SelectionAnswer>
      <StyleSuggestion>radiobutton</StyleSuggestion>
      <Selections>

        <Selection>
        <SelectionIdentifier>q2-a1</SelectionIdentifier>
        <Text>Exploitation colonialism</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q2-invalid</SelectionIdentifier>
        <Text>N/A: Invalid question</Text>
        </Selection>

      </Selections>
    </SelectionAnswer>
  </AnswerSpecification>
</Question>

<Question>
  <QuestionIdentifier>q3</QuestionIdentifier>
  <IsRequired>true</IsRequired>
  <QuestionContent><Text>3. How many colonists?</Text></QuestionContent>
  <AnswerSpecification>
    <SelectionAnswer>
      <StyleSuggestion>radiobutton</StyleSuggestion>
      <Selections>

        <Selection>
        <SelectionIdentifier>q3-a1</SelectionIdentifier>
        <Text>fewer</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q3-invalid</SelectionIdentifier>
        <Text>N/A: Invalid question</Text>
        </Selection>

      </Selections>
    </SelectionAnswer>
  </AnswerSpecification>
</Question>

<Question>
  <QuestionIdentifier>q4</QuestionIdentifier>
  <IsRequired>true</IsRequired>
  <QuestionContent><Text>4. What kind of colonialism?</Text></QuestionContent>
  <AnswerSpecification>
    <SelectionAnswer>
      <StyleSuggestion>radiobutton</StyleSuggestion>
      <Selections>

        <Selection>
        <SelectionIdentifier>q4-a1</SelectionIdentifier>
        <Text>Exploitation</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q4-redundant</SelectionIdentifier>
        <Text>N/A: Redundant with question 2</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q4-invalid</SelectionIdentifier>
        <Text>N/A: Invalid question</Text>
        </Selection>

      </Selections>
    </SelectionAnswer>
  </AnswerSpecification>
</Question>

<Question>
  <QuestionIdentifier>q5</QuestionIdentifier>
  <IsRequired>true</IsRequired>
  <QuestionContent><Text>5. What form of colonialism involves fewer colonists?</Text></QuestionContent>
  <AnswerSpecification>
    <SelectionAnswer>
      <StyleSuggestion>radiobutton</StyleSuggestion>
      <Selections>

        <Selection>
        <SelectionIdentifier>q5-a1</SelectionIdentifier>
        <Text>Exploitation</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q5-redundant1</SelectionIdentifier>
        <Text>N/A: Redundant with question 2</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q5-redundant2</SelectionIdentifier>
        <Text>N/A: Redundant with question 4</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q5-invalid</SelectionIdentifier>
        <Text>N/A: Invalid question</Text>
        </Selection>

      </Selections>
    </SelectionAnswer>
  </AnswerSpecification>
</Question>

<Question>
  <QuestionIdentifier>q6</QuestionIdentifier>
  <IsRequired>true</IsRequired>
  <QuestionContent><Text>6. Fewer what?</Text></QuestionContent>
  <AnswerSpecification>
    <SelectionAnswer>
      <StyleSuggestion>radiobutton</StyleSuggestion>
      <Selections>

        <Selection>
        <SelectionIdentifier>q6-a1</SelectionIdentifier>
        <Text>colonists</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q6-redundant</SelectionIdentifier>
        <Text>N/A: Redundant with question 3</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q6-invalid</SelectionIdentifier>
        <Text>N/A: Invalid question</Text>
        </Selection>

      </Selections>
    </SelectionAnswer>
  </AnswerSpecification>
</Question>

<Question>
  <QuestionIdentifier>q7</QuestionIdentifier>
  <IsRequired>true</IsRequired>
  <QuestionContent><Text>7. What is exploited?</Text></QuestionContent>
  <AnswerSpecification>
    <SelectionAnswer>
      <StyleSuggestion>radiobutton</StyleSuggestion>
      <Selections>

        <Selection>
        <SelectionIdentifier>q7-a1</SelectionIdentifier>
        <Text>colonialism</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q7-a2</SelectionIdentifier>
        <Text>resources</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q7-redundant1</SelectionIdentifier>
        <Text>N/A: Redundant with question 4</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q7-invalid</SelectionIdentifier>
        <Text>N/A: Invalid question</Text>
        </Selection>

      </Selections>
    </SelectionAnswer>
  </AnswerSpecification>
</Question>

<Question>
  <QuestionIdentifier>q8</QuestionIdentifier>
  <IsRequired>true</IsRequired>
  <QuestionContent><Text>8. Where do the exports typically go?</Text></QuestionContent>
  <AnswerSpecification>
    <SelectionAnswer>
      <StyleSuggestion>radiobutton</StyleSuggestion>
      <Selections>

        <Selection>
        <SelectionIdentifier>q8-a1</SelectionIdentifier>
        <Text>the metropole</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q8-invalid</SelectionIdentifier>
        <Text>N/A: Invalid question</Text>
        </Selection>

      </Selections>
    </SelectionAnswer>
  </AnswerSpecification>
</Question>

<Question>
  <QuestionIdentifier>q9</QuestionIdentifier>
  <IsRequired>true</IsRequired>
  <QuestionContent><Text>9. Does it focus more on colonists or access to resources?</Text></QuestionContent>
  <AnswerSpecification>
    <SelectionAnswer>
      <StyleSuggestion>radiobutton</StyleSuggestion>
      <Selections>

        <Selection>
        <SelectionIdentifier>q9-a1</SelectionIdentifier>
        <Text>colonists</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q9-a2</SelectionIdentifier>
        <Text>access to resources</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q9-invalid</SelectionIdentifier>
        <Text>N/A: Invalid question</Text>
        </Selection>

      </Selections>
    </SelectionAnswer>
  </AnswerSpecification>
</Question>

<Question>
  <QuestionIdentifier>q10</QuestionIdentifier>
  <IsRequired>true</IsRequired>
  <QuestionContent><Text>10. What gets exported?</Text></QuestionContent>
  <AnswerSpecification>
    <SelectionAnswer>
      <StyleSuggestion>radiobutton</StyleSuggestion>
      <Selections>

        <Selection>
        <SelectionIdentifier>q10-a1</SelectionIdentifier>
        <Text>access to resources</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q10-a2</SelectionIdentifier>
        <Text>resources</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q10-invalid</SelectionIdentifier>
        <Text>N/A: Invalid question</Text>
        </Selection>

      </Selections>
    </SelectionAnswer>
  </AnswerSpecification>
</Question>

<Question>
  <QuestionIdentifier>q11</QuestionIdentifier>
  <IsRequired>true</IsRequired>
  <QuestionContent><Text>11. What is the last word of the Centre?</Text></QuestionContent>
  <AnswerSpecification>
    <SelectionAnswer>
      <StyleSuggestion>radiobutton</StyleSuggestion>
      <Selections>

        <Selection>
        <SelectionIdentifier>q11-a1</SelectionIdentifier>
        <Text>Studies</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q11-invalid</SelectionIdentifier>
        <Text>N/A: Invalid question</Text>
        </Selection>

      </Selections>
    </SelectionAnswer>
  </AnswerSpecification>
</Question>

<Question>
  <QuestionIdentifier>q12</QuestionIdentifier>
  <IsRequired>true</IsRequired>
  <QuestionContent><Text>12. What is the Centre's full name?</Text></QuestionContent>
  <AnswerSpecification>
    <SelectionAnswer>
      <StyleSuggestion>radiobutton</StyleSuggestion>
      <Selections>

        <Selection>
        <SelectionIdentifier>q12-a1</SelectionIdentifier>
        <Text>Nonexistent Centre for Imperialism Studies</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q12-invalid</SelectionIdentifier>
        <Text>N/A: Invalid question</Text>
        </Selection>

      </Selections>
    </SelectionAnswer>
  </AnswerSpecification>
</Question>

<Question>
  <QuestionIdentifier>q13</QuestionIdentifier>
  <IsRequired>true</IsRequired>
  <QuestionContent><Text>13. What does the Centre study?</Text></QuestionContent>
  <AnswerSpecification>
    <SelectionAnswer>
      <StyleSuggestion>radiobutton</StyleSuggestion>
      <Selections>

        <Selection>
        <SelectionIdentifier>q13-a1</SelectionIdentifier>
        <Text>Imperialism</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q13-invalid</SelectionIdentifier>
        <Text>N/A: Invalid question</Text>
        </Selection>

      </Selections>
    </SelectionAnswer>
  </AnswerSpecification>
</Question>

<Question>
  <QuestionIdentifier>q14</QuestionIdentifier>
  <IsRequired>true</IsRequired>
  <QuestionContent><Text>14. Is the Centre existent?</Text></QuestionContent>
  <AnswerSpecification>
    <SelectionAnswer>
      <StyleSuggestion>radiobutton</StyleSuggestion>
      <Selections>

        <Selection>
        <SelectionIdentifier>q14-a1</SelectionIdentifier>
        <Text>Nonexistent</Text>
        </Selection>

        <Selection>
        <SelectionIdentifier>q14-invalid</SelectionIdentifier>
        <Text>N/A: Invalid question</Text>
        </Selection>

      </Selections>
    </SelectionAnswer>
  </AnswerSpecification>
</Question>

</QuestionForm>
""".trim

  private[this] def answerXML(qid: String, aid: String): String = answerXML(qid, List(aid))
  private[this] def answerXML(qid: String, aids: List[String]): String = {
    val opts = aids.map(aid => s"""
<AnswerOption>
  <SelectionIdentifier>$aid</SelectionIdentifier>
  <AnswerScore>1</AnswerScore>
</AnswerOption>
""".trim).mkString("\n")
    s"""
<Question>
<QuestionIdentifier>$qid</QuestionIdentifier>
$opts
</Question>
""".trim

  }
  override val answerKeyString = s"""<?xml version="1.0" encoding="UTF-8"?>
<AnswerKey xmlns="http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2005-10-01/AnswerKey.xsd">
${answerXML("q1", "q1-invalid")}
${answerXML("q2", "q2-a1")}
${answerXML("q3", "q3-a1")}
${answerXML("q4", "q4-a1")}
${answerXML("q5", "q5-redundant2")}
${answerXML("q6", "q6-a1")}
${answerXML("q7", List("q7-invalid", "q7-a2"))}
${answerXML("q8", "q8-a1")}
${answerXML("q9", "q9-invalid")}
${answerXML("q10", "q10-a2")}
${answerXML("q11", "q11-invalid")}
${answerXML("q12", "q12-a1")}
${answerXML("q13", "q13-a1")}
${answerXML("q14", "q14-invalid")}
<QualificationValueMapping>
  <PercentageMapping>
    <MaximumSummedScore>14</MaximumSummedScore>
  </PercentageMapping>
</QualificationValueMapping>
</AnswerKey>
""".trim

}
