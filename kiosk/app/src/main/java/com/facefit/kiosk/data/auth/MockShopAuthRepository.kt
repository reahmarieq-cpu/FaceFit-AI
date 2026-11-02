package com.facefit.kiosk.data.auth

import com.facefit.kiosk.model.ShopAccount

class MockShopAuthRepository : ShopAuthRepository {

    override fun signIn(email: String, password: String): AuthResult {
        val normalizedEmail = email.trim().lowercase()
        return if (normalizedEmail == DEMO_EMAIL && password.isNotBlank()) {
            AuthResult.Success(
                ShopAccount(
                    id = "demo-shop",
                    displayName = "FaceFit Demo Shop",
                    email = DEMO_EMAIL
                )
            )
        } else {
            AuthResult.InvalidCredentials
        }
    }

    private companion object {
        const val DEMO_EMAIL = "demo@facefit.local"
    }
}
