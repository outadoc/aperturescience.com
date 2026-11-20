package com.aperturescience.terminal.minitel

import com.aperturescience.terminal.TextAnnotation
import kotlinx.serialization.Serializable

/**
 * Serializable mirror of `logic`'s [TextAnnotation] - kotlinx.serialization has no built-in
 * `IntRange` serializer, so [end] is exclusive (matching [TextAnnotation.range]'s `start until end`)
 * rather than [TextAnnotation.range]'s own inclusive `last`.
 */
@Serializable
data class MinitelTextAnnotation(
    val tag: String,
    val start: Int,
    val end: Int,
)

fun TextAnnotation.toData(): MinitelTextAnnotation =
    MinitelTextAnnotation(
        tag = tag,
        start = range.first,
        end = range.last + 1,
    )

fun MinitelTextAnnotation.toDomain(): TextAnnotation = TextAnnotation(tag = tag, range = start until end)
