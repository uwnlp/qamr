package qamr.util

import scalacss.DevDefaults._
import scala.language.postfixOps

object Styles extends StyleSheet.Inline {
  import dsl._

  val mainContent = style(
    font := "Helvetica"
  )

  val unselectable = style(
    userSelect := "none"
  )

  val answerIndicator = style(
    color(c"rgb(20, 180, 20)")
  )

  val listlessList = style(
    margin(0 px),
    padding(0 px),
    listStyleType := "none"
  )

  val specialWord = style(
    fontWeight.bold,
    textDecoration := "underline"
  )

  val goodGreen = style(
    color(c"rgb(48, 140, 20)"),
    fontWeight.bold
  )

  val badRed = style(
    color(c"rgb(216, 31, 00)"),
    fontWeight.bold
  )

  val bolded = style(fontWeight.bold)

  val niceBlue = style(
    style(fontWeight.bold),
    color(c"rgb(50, 164, 251)"))

  val uncomfortableOrange = style(
    color(c"rgb(255, 135, 0)")
  )
}
