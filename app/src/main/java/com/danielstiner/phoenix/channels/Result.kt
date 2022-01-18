package com.danielstiner.phoenix.channels

/**
 * A generic class that holds either a value or an exception.
 * @param <T>
 */
sealed class Result<out T : Any> {

    data class Ok<out T : Any>(val value: T) : Result<T>()
    data class Err(val error: Exception) : Result<Nothing>()

    override fun toString(): String {
        return when (this) {
            is Ok<*> -> "Success($value)"
            is Err -> "Error($error)"
        }
    }
}
