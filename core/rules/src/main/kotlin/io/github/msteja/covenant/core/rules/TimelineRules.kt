package io.github.msteja.covenant.core.rules

import io.github.msteja.covenant.core.model.Block
import io.github.msteja.covenant.core.model.MINUTES_PER_DAY
import io.github.msteja.covenant.core.model.Timeline
import io.github.msteja.covenant.core.model.toMinuteOfDay

const val MAX_BLOCKS = 5
const val MIN_BLOCK_MINUTES = 30

sealed interface TimelineProblem {
    /** R2: a timeline holds at most [MAX_BLOCKS] blocks. */
    data class TooManyBlocks(val count: Int) : TimelineProblem

    /** R2: blocks last at least [MIN_BLOCK_MINUTES] minutes. */
    data class TooShort(val blockId: String) : TimelineProblem

    /** R2: blocks can't overlap. */
    data class Overlap(val firstId: String, val secondId: String) : TimelineProblem

    data class BlankName(val blockId: String) : TimelineProblem

    data class DuplicateId(val blockId: String) : TimelineProblem
}

/** Everything wrong with this timeline under R2. Empty means it can be saved. */
fun Timeline.problems(): List<TimelineProblem> = buildList {
    if (blocks.size > MAX_BLOCKS) add(TimelineProblem.TooManyBlocks(blocks.size))
    blocks.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { add(TimelineProblem.DuplicateId(it)) }
    for (block in blocks) {
        if (block.name.isBlank()) add(TimelineProblem.BlankName(block.id))
        if (block.length.toMinutes() < MIN_BLOCK_MINUTES) add(TimelineProblem.TooShort(block.id))
    }
    for (i in blocks.indices) {
        for (j in i + 1 until blocks.size) {
            if (blocks[i].overlaps(blocks[j])) add(TimelineProblem.Overlap(blocks[i].id, blocks[j].id))
        }
    }
}

/** Minute ranges on the 24-hour clock, split in two when the block crosses midnight. */
private fun Block.minuteRanges(): List<IntRange> {
    val from = start.toMinuteOfDay()
    val until = from + length.toMinutes().toInt()
    return if (until <= MINUTES_PER_DAY) {
        listOf(from until until)
    } else {
        listOf(from until MINUTES_PER_DAY, 0 until until - MINUTES_PER_DAY)
    }
}

private fun Block.overlaps(other: Block): Boolean =
    minuteRanges().any { a -> other.minuteRanges().any { b -> a.first < b.last + 1 && b.first < a.last + 1 } }
