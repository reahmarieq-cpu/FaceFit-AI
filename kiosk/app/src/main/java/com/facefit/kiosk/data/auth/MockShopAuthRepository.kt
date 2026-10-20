package com.facefit.kiosk.data.auth

import com.facefit.kiosk.model.ShopAccount

class MockShopAuthRepository : ShopAuthRepository {

    override fun signIn(email: String, password: String): AuthResult {
        val normalizedEmail = email.trim().lowercase()
        return if (normalizedEmail == MOCK_EMAIL && password == MOCK_PASSWORD) {
            AuthResult.Success(
                ShopAccount(
                    id = "mock-clearview",
                    displayName = "ClearView Optical",
                    email = MOCK_EMAIL
                )
            )
        } else {
            AuthResult.InvalidCredentials
        }
    }

    private companion object {
        const val MOCK_EMAIL = "personnel@clearview.ph"
        const val MOCK_PASSWORD = "facefit123"
    }
}

