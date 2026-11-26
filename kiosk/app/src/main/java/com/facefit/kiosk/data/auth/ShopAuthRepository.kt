package com.facefit.kiosk.data.auth

import com.facefit.kiosk.model.ShopAccount

interface ShopAuthRepository {
    val currentShopId: String? get() = null
    suspend fun restore(): AuthResult = AuthResult.InvalidCredentials
    fun signOut() {}
    suspend fun signIn(email: String, password: String): AuthResult
}

sealed class AuthResult {
    data class Success(val shop: ShopAccount) : AuthResult()
    data object InvalidCredentials : AuthResult()
    data object ShopNotAuthorized : AuthResult()
    data object BackendUnavailable : AuthResult()
}
