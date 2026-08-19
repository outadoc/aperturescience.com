package com.aperturescience.terminal

import com.aperturescience.terminal.data.Question
import com.aperturescience.terminal.data.QuestionType
import com.aperturescience.terminal.data.TerminalData
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A generic acceptable answer for any question, regardless of its type.
 */
private fun genericAnswerFor(question: Question): String = if (question.type == QuestionType.TEXT) "AN ANSWER" else "1"

private fun TestScope.answerQuestions(
    engine: TerminalEngine,
    questions: List<Question>,
) {
    for (question in questions) {
        submit(engine, genericAnswerFor(question))
    }
}

private fun TestScope.enterApplication(engine: TerminalEngine = loginToShell()): TerminalEngine {
    submit(engine, "APPLY")
    submit(engine, "CONTINUE")
    submit(engine, "CONTINUE")
    return engine
}

class ApplicationWizardTest {
    @Test
    fun `APPLY shows the application intro screen`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "APPLY")
            assertTrue(
                engine.state.value.displayText
                    .contains("ENRICHMENT CENTER TEST SUBJECT APPLICATION"),
            )
        }

    @Test
    fun `QUIT at the intro screen returns to the shell`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "APPLY")
            submit(engine, "QUIT")
            assertTrue(
                engine.state.value.displayText
                    .contains("B:\\>"),
            )
        }

    @Test
    fun `CONTINUE at the intro screen shows the UID screen`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "APPLY")
            submit(engine, "CONTINUE")
            assertTrue(
                engine.state.value.displayText
                    .contains("Unique Indentity Number"),
            )
            assertTrue(
                engine.state.value.displayText
                    .contains("["),
            )
        }

    @Test
    fun `QUIT at the UID screen returns to the shell`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "APPLY")
            submit(engine, "CONTINUE")
            submit(engine, "QUIT")
            assertTrue(
                engine.state.value.displayText
                    .contains("B:\\>"),
            )
        }

    @Test
    fun `CONTINUE at the intro screen sets a blink annotation covering exactly the bracketed UID`() =
        runTest {
            val engine = loginToShell()
            submit(engine, "APPLY")
            submit(engine, "CONTINUE")
            val annotation =
                engine.state.value.annotations
                    .singleOrNull { it.tag == BLINK_TAG }
            assertTrue(annotation != null)
            val range = annotation.range
            assertEquals(
                "[${engine.state.value.uid}]",
                engine.state.value.displayText.substring(
                    range.first,
                    range.last + 1,
                ),
            )
        }

    @Test
    fun `annotations are empty on screens with no blinking content`() =
        runTest {
            val engine = loginToShell()
            assertTrue(
                engine.state.value.annotations
                    .isEmpty(),
            )
            submit(engine, "APPLY")
            assertTrue(
                engine.state.value.annotations
                    .isEmpty(),
            )
        }

    @Test
    fun `a narrow viewport wraps the UID across lines without losing or duplicating a character`() =
        runTest {
            val engine = loginToShell()
            engine.dispatch(Intent.ViewportResized(20)) // forces the 66-char bracketed UID to hard-wrap
            submit(engine, "APPLY")
            submit(engine, "CONTINUE")
            val range =
                engine.state.value.annotations
                    .single { it.tag == BLINK_TAG }
                    .range
            val blinkingText =
                engine.state.value.displayText
                    .substring(range.first, range.last + 1)
                    .replace("\n", "")
            assertEquals("[${engine.state.value.uid}]", blinkingText)
        }

    @Test
    fun `CONTINUE at the UID screen shows question 1`() =
        runTest {
            val engine = enterApplication()
            assertTrue(
                engine.state.value.displayText
                    .contains("Page 1"),
            )
            assertTrue(
                engine.state.value.displayText
                    .contains(TerminalData.questions[0].text),
            )
        }

    @Test
    fun `any free-text answer to a TEXT question advances to the next question`() =
        runTest {
            val engine = enterApplication()
            check(TerminalData.questions[0].type == QuestionType.TEXT) { "test assumes Q1 is free text" }
            submit(engine, "WHATEVER I FEEL LIKE TYPING")
            assertTrue(
                engine.state.value.displayText
                    .contains("Page 2"),
            )
        }

    @Test
    fun `an out-of-range numeric answer to a choice question is rejected`() =
        runTest {
            val engine = enterApplication()
            submit(engine, genericAnswerFor(TerminalData.questions[0])) // -> question 2, a choice question
            val question2 = TerminalData.questions[1]
            check(question2.type != QuestionType.TEXT) { "test assumes Q2 is a choice question" }

            val before = engine.state.value.displayText
            submit(engine, (question2.choices.size + 99).toString())
            assertEquals(before, engine.state.value.displayText)
        }

    @Test
    fun `a non-numeric answer to a choice question is rejected`() =
        runTest {
            val engine = enterApplication()
            submit(engine, genericAnswerFor(TerminalData.questions[0])) // -> question 2
            val before = engine.state.value.displayText
            submit(engine, "NOT A NUMBER")
            assertEquals(before, engine.state.value.displayText)
        }

    @Test
    fun `a valid numeric answer to a choice question advances`() =
        runTest {
            val engine = enterApplication()
            submit(engine, genericAnswerFor(TerminalData.questions[0])) // -> question 2
            submit(engine, "1")
            assertTrue(
                engine.state.value.displayText
                    .contains("Page 3"),
            )
        }

    @Test
    fun `QUIT during the questionnaire abandons the form and returns to the shell`() =
        runTest {
            val engine = enterApplication()
            submit(engine, "QUIT")
            assertTrue(
                engine.state.value.displayText
                    .contains("B:\\>"),
            )
        }

    @Test
    fun `answering all fifty questions ends the form with the UIN prompt`() =
        runTest {
            val engine = enterApplication()
            answerQuestions(engine, TerminalData.questions)

            val output = engine.state.value.displayText
            assertTrue(output.contains("Congratulations!"))
            assertTrue(output.contains("64 digit UIN"))
        }

    @Test
    fun `THECAKEISALIE as the UIN answer enters the cake easter egg`() =
        runTest {
            val engine = enterApplication()
            answerQuestions(engine, TerminalData.questions)
            submit(engine, "THECAKEISALIE")
            assertTrue(
                engine.state.value.displayText
                    .contains("left the building"),
            )
        }

    @Test
    fun `any other UIN answer leads to a dead end that accepts no further input`() =
        runTest {
            val engine = enterApplication()
            answerQuestions(engine, TerminalData.questions)
            submit(engine, "0000000000000000")

            val deadEnd = engine.state.value.displayText
            assertTrue(deadEnd.contains("does not match"))
            assertTrue(deadEnd.contains("REMAIN AT YOUR WORKSTATION"))

            submit(engine, "ANYTHING")
            assertEquals(deadEnd, engine.state.value.displayText)
            submit(engine, "LOGON")
            assertEquals(deadEnd, engine.state.value.displayText)
        }

    @Test
    fun `question 21 has more than one page of choices`() =
        runTest {
            val engine = enterApplication()
            answerQuestions(engine, TerminalData.questions.take(20))

            val question21 = TerminalData.questions[20]
            assertEquals(21, question21.index)
            assertTrue(question21.choices.size > 104)
            assertTrue(
                engine.state.value.displayText
                    .contains("${question21.choices.size} total choices"),
            )
        }

    @Test
    fun `PageDown then PageUp on question 21 returns to the original view`() =
        runTest {
            val engine = enterApplication()
            answerQuestions(engine, TerminalData.questions.take(20))

            val firstPage = engine.state.value.displayText
            pressKey(engine, "PageDown")
            val secondPage = engine.state.value.displayText
            assertNotEquals(firstPage, secondPage)

            pressKey(engine, "PageUp")
            assertEquals(firstPage, engine.state.value.displayText)
        }

    /**
     * Matches the original: overshooting past the final page reverts to the untouched offset -
     * PageDown there is a no-op, never landing on a spurious page isolating the last choice.
     */
    @Test
    fun `PageDown past the final page of question 21 is a no-op`() =
        runTest {
            val engine = enterApplication()
            answerQuestions(engine, TerminalData.questions.take(20))

            val totalChoices = TerminalData.questions[20].choices.size
            val pressesToLastPage = totalChoices / PAGE_SIZE
            repeat(pressesToLastPage) { pressKey(engine, "PageDown") }

            val lastPage = engine.state.value.displayText
            assertTrue(lastPage.endsWith("Zorilla\n[$totalChoices total choices : PGUP/PGDN to navigate]> "))

            pressKey(engine, "PageDown")
            assertEquals(lastPage, engine.state.value.displayText)
        }

    @Test
    fun `PageUp does nothing on the first page of question 21`() =
        runTest {
            val engine = enterApplication()
            answerQuestions(engine, TerminalData.questions.take(20))

            val firstPage = engine.state.value.displayText
            pressKey(engine, "PageUp")
            assertEquals(firstPage, engine.state.value.displayText)
        }

    @Test
    fun `PageUp and PageDown do nothing on a small choice question`() =
        runTest {
            val engine = enterApplication()
            submit(engine, genericAnswerFor(TerminalData.questions[0])) // -> question 2 (5 choices)
            check(TerminalData.questions[1].choices.size <= 104)

            val before = engine.state.value.displayText
            pressKey(engine, "PageDown")
            assertEquals(before, engine.state.value.displayText)
            pressKey(engine, "PageUp")
            assertEquals(before, engine.state.value.displayText)
        }

    @Test
    fun `PageUp and PageDown do nothing outside the application questionnaire`() =
        runTest {
            val engine = loginToShell()
            val before = engine.state.value.displayText
            pressKey(engine, "PageDown")
            assertEquals(before, engine.state.value.displayText)
        }

    @Test
    fun `the questionnaire never reaches exitRequested on its own`() =
        runTest {
            val engine = enterApplication()
            answerQuestions(engine, TerminalData.questions)
            assertFalse(engine.state.value.exitRequested)
        }
}
