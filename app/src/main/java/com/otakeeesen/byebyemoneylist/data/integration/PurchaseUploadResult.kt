package com.otakeeesen.byebyemoneylist.data.integration

sealed class PurchaseUploadResult {
    data class Success(
        val sentAt: Long,
        val alreadyImported: Boolean = false,
    ) : PurchaseUploadResult()

    data object NotConfigured : PurchaseUploadResult()

    data object NotEligible : PurchaseUploadResult()

    data object AuthError : PurchaseUploadResult()

    data object AppNotFound : PurchaseUploadResult()

    data class Rejected(val message: String) : PurchaseUploadResult()

    data object NetworkError : PurchaseUploadResult()
}

open class PurchaseUploadException(message: String) : Exception(message)

class PurchaseUploadAuthException : PurchaseUploadException("Authentication failed")

class PurchaseUploadAppNotFoundException : PurchaseUploadException("Nextcloud app not found")

class PurchaseUploadRejectedException(message: String) : PurchaseUploadException(message)

class PurchaseUploadNetworkException(cause: Throwable? = null) :
    PurchaseUploadException(cause?.message ?: "Network error")
