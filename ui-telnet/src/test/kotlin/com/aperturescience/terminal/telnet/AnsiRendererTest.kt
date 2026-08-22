package com.aperturescience.terminal.telnet

import com.aperturescience.terminal.BLINK_TAG
import com.aperturescience.terminal.EasterEgg
import com.aperturescience.terminal.EngineState
import com.aperturescience.terminal.Mode
import com.aperturescience.terminal.TextAnnotation
import com.aperturescience.terminal.WRAP_WIDTH
import kotlin.test.Test
import kotlin.test.assertEquals

private const val ESC = ""
private const val HIDE = "$ESC[?25l"
private const val SHOW = "$ESC[?25h"
private const val HOME = "$ESC[H"
private const val ERASE = "$ESC[0J"
private const val BLINK_ON = "$ESC[5m"
private const val BLINK_OFF = "$ESC[25m"
private const val UNDERLINE_ON = "$ESC[4m"
private const val UNDERLINE_OFF = "$ESC[24m"

private fun cup(
    row: Int,
    col: Int,
) = "$ESC[$row;${col}H"

private fun fixture(
    pageContent: String,
    input: String = "",
    annotations: List<TextAnnotation> = emptyList(),
) = EngineState(
    mode = Mode.Login.Initial,
    isAdmin = false,
    uid = "",
    pageContent = pageContent,
    input = input,
    wrapWidth = WRAP_WIDTH,
    isLocked = false,
    annotations = annotations,
)

class AnsiRendererTest {
    @Test
    fun `plain single-line text places the cursor right after it with no trailing newline`() {
        val frame = renderFrame(fixture(pageContent = "HELLO"))
        assertEquals(HIDE + HOME + ERASE + "HELLO" + cup(1, 6) + SHOW, frame)
    }

    @Test
    fun `multi-line text joins rows with CRLF and places the cursor on the last row`() {
        val frame = renderFrame(fixture(pageContent = "AB\nC"))
        assertEquals(HIDE + HOME + ERASE + "AB" + "\r\n" + "C" + cup(2, 2) + SHOW, frame)
    }

    @Test
    fun `blink annotation wraps its span in SGR 5 on off`() {
        val frame =
            renderFrame(
                fixture(
                    pageContent = "ABCDE",
                    annotations = listOf(TextAnnotation(BLINK_TAG, 0..2)),
                ),
            )
        assertEquals(
            HIDE + HOME + ERASE + BLINK_ON + "ABC" + BLINK_OFF + "DE" + cup(1, 6) + SHOW,
            frame,
        )
    }

    @Test
    fun `store easter egg annotation wraps its span in underline on off`() {
        val frame =
            renderFrame(
                fixture(
                    pageContent = "ABCDEFG",
                    annotations = listOf(TextAnnotation(EasterEgg.STORE.tag, 2..4)),
                ),
            )
        assertEquals(
            HIDE + HOME + ERASE + "AB" + UNDERLINE_ON + "CDE" + UNDERLINE_OFF + "FG" + cup(1, 8) + SHOW,
            frame,
        )
    }

    @Test
    fun `not-yet-submitted input is appended after page content and moves the cursor`() {
        val frame = renderFrame(fixture(pageContent = "> ", input = "HI"))
        assertEquals(HIDE + HOME + ERASE + "> HI" + cup(1, 5) + SHOW, frame)
    }
}
