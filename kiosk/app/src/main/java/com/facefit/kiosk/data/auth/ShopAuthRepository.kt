package com.facefit.kiosk.data.auth

import com.facefit.kiosk.model.ShopAccount

interface ShopAuthRepository {
    fun signIn(email: String, password: String): AuthResult
}

sealed class AuthResult {
    data class Success(val shop: ShopAccount) : AuthResult()
    data object InvalidCredentials : AuthResult()
    data object BackendUnavailable : AuthResult()
}

