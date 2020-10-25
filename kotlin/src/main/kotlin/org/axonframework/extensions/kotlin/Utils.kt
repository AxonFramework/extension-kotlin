package org.axonframework.extensions.kotlin

/**
 * Tries to execute the given function or reports an error on failure.
 * @param errorMessage message to report on error.
 * @param function: function to invoke
 */
@Throws(IllegalArgumentException::class)
internal fun <T : Any?> invokeReporting(errorMessage: String, function: () -> T): T {
    return try {
        function.invoke()
    } catch (e: Exception) {
        throw IllegalArgumentException(errorMessage, e)
    }
}
