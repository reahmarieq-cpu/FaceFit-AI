package com.facefit.kiosk.data.auth

import com.facefit.kiosk.model.ShopAccount

class MockShopAuthRepository : ShopAuthRepository {

    override suspend fun signIn(email: String, password: String): AuthResult {
        val normalizedEmail = email.trim().lowercase()
        return if (normalizedEmail == DEMO_EMAIL && password.isNotBlank()) {
            AuthResult.Success(demoShop)
        } else {
            AuthResult.InvalidCredentials
        }
    }

    companion object {
        private const val DEMO_EMAIL = "demo@facefit.local"
        val demoShop = ShopAccount("demo-shop", "FaceFit Optical (Demo)", DEMO_EMAIL)
    }
}
