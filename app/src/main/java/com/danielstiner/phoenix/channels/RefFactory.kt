package com.danielstiner.phoenix.channels

import java.util.concurrent.atomic.AtomicLong

class RefFactory {
    var state = AtomicLong(0L)
    fun newRef(): Ref {
        return Ref(state.getAndIncrement().toString())
    }
}
