package com.facefit.kiosk.ui.auth

import android.content.Context
import android.graphics.Color
import android.util.Patterns
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import com.facefit.kiosk.R
import kotlinx.coroutines.CancellationException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.facefit.kiosk.data.auth.AuthResult
import com.facefit.kiosk.data.auth.ShopAuthRepository
import com.facefit.kiosk.data.session.KioskSessionStore
import com.facefit.kiosk.databinding.ScreenShopLoginBinding
import com.facefit.kiosk.navigation.KioskDestination
import com.facefit.kiosk.navigation.KioskNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ShopLoginView(
    context: Context,
    private val authRepository: ShopAuthRepository,
    private val sessionStore: KioskSessionStore,
    private val navigator: KioskNavigator
) : FrameLayout(context) {

    private val binding = ScreenShopLoginBinding.inflate(LayoutInflater.from(context), this, true)

    private var loginJob: Job? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (authRepository.currentShopId != null) {
            authenticate(restoring = true) { authRepository.restore() }
        }
    }

    override fun onDetachedFromWindow() {
        loginJob?.cancel()
        loginJob = null
        super.onDetachedFromWindow()
    }

    init {
        binding.emailInput.setTextColor(Color.WHITE)
        binding.passwordInput.setTextColor(Color.WHITE)

        binding.signInButton.setOnClickListener { submitLogin() }
        binding.passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitLogin()
                true
            } else {
                false
            }
        }
    }

    private fun submitLogin() {
        if (loginJob?.isActive == true) return
        clearErrors()

        val email = binding.emailInput.text?.toString().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()
        if (!validate(email, password)) return

        authenticate { authRepository.signIn(email, password) }
    }

    private fun authenticate(restoring: Boolean = false, request: suspend () -> AuthResult) {
        binding.signInButton.isEnabled = false
        binding.emailInput.isEnabled = false
        binding.passwordInput.isEnabled = false
        loginJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                when (val result = request()) {
                    is AuthResult.Success -> {
                        check(authRepository.currentShopId == result.shop.id)
                        binding.passwordInput.text?.clear()
                        sessionStore.clearCustomerSession()
                        sessionStore.activeShop = result.shop
                        navigator.navigateTo(KioskDestination.Welcome)
                    }
                    AuthResult.InvalidCredentials -> {
                        if (!restoring) {
                            showLoginError(R.string.shop_login_invalid)
                            binding.passwordLayout.error = " "
                        }
                    }
                    AuthResult.ShopNotAuthorized -> showLoginError(R.string.shop_login_unauthorized)
                    AuthResult.BackendUnavailable -> showLoginError(R.string.shop_login_backend_error)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                authRepository.signOut()
                showLoginError(if (error is FirebaseFirestoreException &&
                    error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    R.string.shop_login_unauthorized
                } else R.string.shop_login_backend_error)
            } finally {
                binding.emailInput.isEnabled = true
                binding.passwordInput.isEnabled = true
                binding.signInButton.isEnabled = true
            }
        }
    }

    private fun showLoginError(message: Int) {
        binding.generalErrorText.setText(message)
        binding.generalErrorText.visibility = VISIBLE
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
