package com.facefit.kiosk.ui.auth

import android.content.Context
import android.util.Patterns
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.Toast
import com.facefit.kiosk.R
import com.facefit.kiosk.data.auth.AuthResult
import com.facefit.kiosk.data.auth.ShopAuthRepository
import com.facefit.kiosk.data.session.KioskSessionStore
import com.facefit.kiosk.databinding.ScreenShopLoginBinding
import com.facefit.kiosk.navigation.KioskDestination
import com.facefit.kiosk.navigation.KioskNavigator

class ShopLoginView(
    context: Context,
    private val authRepository: ShopAuthRepository,
    private val sessionStore: KioskSessionStore,
    private val navigator: KioskNavigator
) : FrameLayout(context) {

    private val binding = ScreenShopLoginBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        binding.signInButton.setOnClickListener { submitLogin() }
        binding.passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitLogin()
                true
            } else {
                false
            }
        }
        binding.forgotPasswordText.setOnClickListener {
            Toast.makeText(context, R.string.future_not_ready, Toast.LENGTH_SHORT).show()
        }
    }

    private fun submitLogin() {
        clearErrors()

        val email = binding.emailInput.text?.toString().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()
        if (!validate(email, password)) return

        when (val result = authRepository.signIn(email, password)) {
            is AuthResult.Success -> {
                sessionStore.activeShop = result.shop
                navigator.navigateTo(KioskDestination.Welcome)
            }
            AuthResult.InvalidCredentials -> {
                binding.generalErrorText.text = context.getString(R.string.shop_login_invalid)
                binding.generalErrorText.visibility = VISIBLE
                binding.passwordLayout.error = " "
            }
            AuthResult.BackendUnavailable -> {
                binding.generalErrorText.text = context.getString(R.string.future_not_ready)
                binding.generalErrorText.visibility = VISIBLE
            }
        }
    }

    private fun validate(email: String, password: String): Boolean {
        var isValid = true

        if (email.isBlank()) {
            binding.emailLayout.error = context.getString(R.string.shop_login_required_email)
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            binding.emailLayout.error = context.getString(R.string.shop_login_invalid_email)
            isValid = false
        }

        if (password.isBlank()) {
            binding.passwordLayout.error = context.getString(R.string.shop_login_required_password)
            isValid = false
        }

        return isValid
    }

    private fun clearErrors() {
        binding.generalErrorText.visibility = GONE
        binding.emailLayout.error = null
        binding.passwordLayout.error = null
    }
}

