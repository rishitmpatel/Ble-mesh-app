package com.rishit.blemesh

/** Tiny shared state between MeshService and MainActivity. Main thread only. */
object MeshHub {
    var node: MeshNode? = null
    var listener: ((String) -> Unit)? = null
    val history = ArrayList<String>()

    fun post(line: String) {
        history.add(line)
        listener?.invoke(line)
    }
}
