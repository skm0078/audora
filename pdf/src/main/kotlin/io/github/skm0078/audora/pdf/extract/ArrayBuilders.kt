// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/extract/ArrayBuilders.kt
// Purpose: Growable primitive arrays, so per-character page data is not boxed.

package io.github.skm0078.audora.pdf.extract

internal class IntArrayBuilder(initialCapacity: Int) {
    private var data = IntArray(maxOf(initialCapacity, 1))
    var size = 0
        private set

    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    fun toArray(): IntArray = data.copyOf(size)
}

internal class FloatArrayBuilder(initialCapacity: Int) {
    private var data = FloatArray(maxOf(initialCapacity, 1))
    var size = 0
        private set

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    fun toArray(): FloatArray = data.copyOf(size)
}
