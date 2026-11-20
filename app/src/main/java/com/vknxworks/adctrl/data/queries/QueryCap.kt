package com.vknxworks.adctrl.data.queries

/**
 * Cuts a newest-first list down to its newest [max] rows, or returns null when nothing has to go.
 *
 * The time of the last row kept becomes the point to ask the server for older rows from again, and
 * the server answers that with rows strictly older than it. So rows right after the cut that carry
 * exactly the same time as the last kept row are kept too: otherwise they could never be loaded
 * again. Nothing is cut when the last kept row has no time to continue from.
 */
internal fun <T> capNewest(items: List<T>, max: Int, time: (T) -> String): List<T>? {
    if (max <= 0 || items.size <= max) return null
    val edge = time(items[max - 1])
    if (edge.isBlank()) return null
    var end = max
    while (end < items.size && time(items[end]) == edge) end++
    return if (end >= items.size) null else ArrayList(items.subList(0, end))
}
