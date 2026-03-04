package com.afriserve.smsmanager.utils

/**
 * A sealed class representing the result of an operation.
 * Provides a type-safe way to handle success, error, and loading states.
 *
 * Usage:
 * ```kotlin
 * fun fetchData(): Result<Data> {
 *     return try {
 *         val data = api.getData()
 *         Result.Success(data)
 *     } catch (e: Exception) {
 *         Result.Error(e)
 *     }
 * }
 *
 * when (val result = fetchData()) {
 *     is Result.Success -> handleData(result.data)
 *     is Result.Error -> handleError(result.exception)
 *     is Result.Loading -> showLoading()
 * }
 * ```
 */
sealed class Result<out T> {
    
    /**
     * Represents a successful result with data.
     */
    data class Success<out T>(val data: T) : Result<T>()
    
    /**
     * Represents an error result with an exception.
     */
    data class Error(
        val exception: Throwable,
        val message: String? = exception.localizedMessage
    ) : Result<Nothing>()
    
    /**
     * Represents a loading state.
     */
    object Loading : Result<Nothing>()
    
    /**
     * Returns true if this is a Success result.
     */
    val isSuccess: Boolean get() = this is Success
    
    /**
     * Returns true if this is an Error result.
     */
    val isError: Boolean get() = this is Error
    
    /**
     * Returns true if this is a Loading result.
     */
    val isLoading: Boolean get() = this is Loading
    
    /**
     * Returns the data if this is a Success, or null otherwise.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
    
    /**
     * Returns the data if this is a Success, or the default value otherwise.
     */
    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        else -> default
    }
    
    /**
     * Returns the data if this is a Success, or throws the exception if Error.
     * @throws IllegalStateException if this is Loading.
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception
        is Loading -> throw IllegalStateException("Result is still loading")
    }
    
    /**
     * Transform the success data using the given function.
     */
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> Loading
    }
    
    /**
     * Transform the success data using a function that returns a Result.
     */
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
        is Loading -> Loading
    }
    
    /**
     * Execute the given block if this is a Success.
     */
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }
    
    /**
     * Execute the given block if this is an Error.
     */
    inline fun onError(action: (Throwable) -> Unit): Result<T> {
        if (this is Error) action(exception)
        return this
    }
    
    /**
     * Execute the given block if this is Loading.
     */
    inline fun onLoading(action: () -> Unit): Result<T> {
        if (this is Loading) action()
        return this
    }
    
    companion object {
        /**
         * Create a Success result.
         */
        fun <T> success(data: T): Result<T> = Success(data)
        
        /**
         * Create an Error result from a throwable.
         */
        fun error(throwable: Throwable): Result<Nothing> = Error(throwable)
        
        /**
         * Create an Error result from a message.
         */
        fun error(message: String): Result<Nothing> = Error(Exception(message), message)
        
        /**
         * Create a Loading result.
         */
        fun <T> loading(): Result<T> = Loading
        
        /**
         * Wrap a suspending function call in a Result.
         */
        suspend fun <T> runCatching(block: suspend () -> T): Result<T> {
            return try {
                Success(block())
            } catch (e: Exception) {
                Error(e)
            }
        }
        
        /**
         * Wrap a blocking function call in a Result.
         */
        fun <T> catching(block: () -> T): Result<T> {
            return try {
                Success(block())
            } catch (e: Exception) {
                Error(e)
            }
        }
    }
}

/**
 * Extension to convert a nullable value to a Result.
 */
fun <T> T?.toResult(errorMessage: String = "Value is null"): Result<T> {
    return if (this != null) Result.Success(this) else Result.Error(NullPointerException(errorMessage))
}

/**
 * Extension to combine two Results.
 */
fun <T, R> Result<T>.combine(other: Result<R>): Result<Pair<T, R>> {
    return when {
        this is Result.Success && other is Result.Success -> Result.Success(this.data to other.data)
        this is Result.Error -> this
        other is Result.Error -> other
        else -> Result.Loading
    }
}
