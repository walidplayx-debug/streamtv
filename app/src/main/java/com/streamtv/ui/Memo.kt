package com.streamtv.ui

/**
 * Keeps the last result of a heavy computation over the channel list (filtering, grouping,
 * labelling) for as long as its inputs stay the same objects. Screens are rebuilt every time a tab
 * opens or the player closes; with ten thousand channels, redoing this work each time made switching
 * tabs feel slow.
 */
object Memo {
    private val last = HashMap<String, Pair<List<Any?>, Any?>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> of(name: String, vararg keys: Any?, compute: () -> T): T {
        val inputs = keys.toList()
        val hit = last[name]
        if (hit != null && hit.first.size == inputs.size && hit.first.indices.all { same(hit.first[it], inputs[it]) }) return hit.second as T
        return compute().also { last[name] = inputs to it }
    }

    // Lists are compared by identity (a new playlist is a new object); strings and numbers by value.
    private fun same(a: Any?, b: Any?) = if (a is Collection<*> || b is Collection<*>) a === b else a == b
}
