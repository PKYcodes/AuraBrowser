package com.aura.browser.core.common.result

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

sealed interface AuraResult<out T> {
    data class Success<T>(val data: T) : AuraResult<T>
    data class Error(val exception: Throwable, val message: String = exception.localizedMessage ?: "Unknown error") : AuraResult<Nothing>
    data object Loading : AuraResult<Nothing>
}

fun <T> Flow<T>.asAuraResult(): Flow<AuraResult<T>> = this
    .map<T, AuraResult<T>> { AuraResult.Success(it) }
    .onStart { emit(AuraResult.Loading) }
    .catch { emit(AuraResult.Error(it)) }
