package com.danielstiner.phoenix.absinthe

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

class Subscription<T>(
    private val id: SubscriptionId,
    data: Flow<T>,
    private val connection: Connection
) {

    // TODO make independent cancellation
    private val cancelled = AtomicBoolean(false)
    private val scope = CoroutineScope(connection.scope.coroutineContext)
    private val _data = data.takeWhile { !cancelled.get() }.shareIn(scope, SharingStarted.Lazily)

    val data: SharedFlow<T> = _data

    suspend fun cancel() {
        cancelled.set(true)
        connection.unsubscribe(id)
        scope.cancel()
    }

    fun <R> map(transform: (T) -> R): Subscription<R> {
        return Subscription(id, data.map(transform), connection)
    }
}
