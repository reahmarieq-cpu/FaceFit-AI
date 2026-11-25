package com.example.facefit.ui.auth

import com.example.facefit.ui.components.AppMessages
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.data.auth.AuthErrors
import com.example.facefit.data.auth.AuthManager
import com.example.facefit.data.auth.PasswordPolicy
import com.example.facefit.data.auth.PersonName
import com.example.facefit.ui.components.FaceFitButton
import com.example.facefit.ui.components.FaceFitLogo
import com.example.facefit.ui.components.FaceFitTextField
import com.example.facefit.ui.theme.FaceFitTeal
import com.example.facefit.ui.theme.FaceFitTextPrimary
import com.example.facefit.ui.theme.FaceFitTextSecondary
import com.example.facefit.ui.theme.FacefitTheme
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    authManager: AuthManager,
    onBack: () -> Unit,
    onRegisterSuccess: () -> Unit,
    onSignIn: () -> Unit,
    onRegistered: (email: String) -> Unit = { onSignIn() }
) {
    var firstName by remember { mutableStateOf("") }
    var middleInitial by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var agreeToTerms by remember { mutableStateOf(true) }
    
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Shrink above the keyboard so the focused field scrolls into view.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Back")
            }
            FaceFitLogo(size = 40)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Create account",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = FaceFitTextPrimary
        )
        Text(
            text = "Set up your FaceFit profile in seconds",
            fontSize = 14.sp,
            color = FaceFitTextSecondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        FaceFitTextField(
            value = firstName,
            onValueChange = { firstName = PersonName.capitalizeName(it) },
            capitalization = KeyboardCapitalization.Words,
            label = "First Name",
            placeholder = "Enter first name",
            leadingIcon = Icons.Outlined.Person
        )

        Spacer(modifier = Modifier.height(16.dp))

        FaceFitTextField(
            value = middleInitial,
            onValueChange = { middleInitial = PersonName.cleanInitial(it) },
            label = "M.I. (optional)",
            placeholder = "Enter middle initial",
            leadingIcon = Icons.Outlined.Person
        )

        Spacer(modifier = Modifier.height(16.dp))

        FaceFitTextField(
            value = lastName,
            onValueChange = { lastName = PersonName.capitalizeName(it) },
            capitalization = KeyboardCapitalization.Words,
            label = "Last Name",
            placeholder = "Enter last name",
            leadingIcon = Icons.Outlined.Person
        )

        Spacer(modifier = Modifier.height(16.dp))

        FaceFitTextField(
            value = suffix,
            onValueChange = { suffix = PersonName.typedSuffix(it) },
            label = "Suffix (optional)",
            placeholder = "e.g. Jr., Sr., III",
            leadingIcon = Icons.Outlined.Person
        )

        Spacer(modifier = Modifier.height(16.dp))

        FaceFitTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            placeholder = "Enter your email address",
            leadingIcon = Icons.Outlined.Email
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password field and its hint scroll above the keyboard together.
        val passwordArea = remember { BringIntoViewRequester() }
        var passwordFocused by remember { mutableStateOf(false) }
        val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
        val passwordHint = PasswordPolicy.remainingHint(password)
        LaunchedEffect(passwordFocused, keyboardHeight, passwordHint) {
            if (passwordFocused) passwordArea.bringIntoView()
        }
        Column(
            Modifier
                .bringIntoViewRequester(passwordArea)
                .onFocusChanged { passwordFocused = it.hasFocus }
                .padding(bottom = 8.dp)
        ) {
            FaceFitTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                placeholder = "Enter your password",
                leadingIcon = Icons.Outlined.Lock,
                isPassword = true
            )

            // One-line password rule; rules already met drop out as the user types.
            passwordHint?.let { hint ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = hint,
                    modifier = Modifier.padding(start = 4.dp),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = FaceFitTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Confirm field and its match message also stay above the keyboard.
        val confirmArea = remember { BringIntoViewRequester() }
        var confirmFocused by remember { mutableStateOf(false) }
        val passwordsMatch = confirmPassword.isNotEmpty() && confirmPassword == password
        LaunchedEffect(confirmFocused, keyboardHeight, confirmPassword.isEmpty()) {
            if (confirmFocused) confirmArea.bringIntoView()
        }
        Column(
            Modifier
                .bringIntoViewRequester(confirmArea)
                .onFocusChanged { confirmFocused = it.hasFocus }
                .padding(bottom = 8.dp)
        ) {
            FaceFitTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = "Confirm Password",
                placeholder = "Re-enter your password",
                leadingIcon = Icons.Outlined.Lock,
                isPassword = true
            )
            if (confirmPassword.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (passwordsMatch) "✓  Passwords match" else "Passwords do not match",
                    modifier = Modifier.padding(start = 4.dp),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = if (passwordsMatch) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = agreeToTerms,
                onCheckedChange = { agreeToTerms = it },
                colors = CheckboxDefaults.colors(checkedColor = FaceFitTeal)
            )
            Text(
                text = "I agree to the Terms of Service and Privacy Policy",
                fontSize = 12.sp,
                color = FaceFitTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = FaceFitTeal)
        } else {
            FaceFitButton(
                text = "Create account",
                onClick = {
                    if (!agreeToTerms) {
                        AppMessages.show("Please agree to the Terms of Service and Privacy Policy")
                        return@FaceFitButton
                    }
                    if (firstName.isBlank() || lastName.isBlank() || email.isBlank() || password.isBlank()) {
                        AppMessages.show("Please fill in all fields")
                        return@FaceFitButton
                    }
                    if (!AuthErrors.isValidEmail(email)) {
                        AppMessages.show("Please enter a valid email address.")
                        return@FaceFitButton
                    }
                    if (!PasswordPolicy.isValid(password)) {
                        AppMessages.show(PasswordPolicy.remainingHint(password) ?: PasswordPolicy.HINT, long = true)
                        return@FaceFitButton
                    }
                    if (password != confirmPassword) {
                        AppMessages.show("Passwords do not match")
                        return@FaceFitButton
                    }
                    
                    isLoading = true
                    scope.launch {
                        val result = authManager.signUp(firstName.trim(), lastName.trim(), email.trim(), password,
                            middleInitial = middleInitial, suffix = suffix)
                        result.onSuccess {
                            // Firebase signs a new account in; the user signs in themselves instead.
                            authManager.signOut(context)
                            isLoading = false
                            AppMessages.show("Account created. Please sign in.")
                            onRegistered(email.trim())
                        }.onFailure { e ->
                            // The account itself may exist (only the profile save failed):
                            // don't leave it half signed in on this screen.
                            val accountCreated = authManager.currentUser != null
                            if (accountCreated) authManager.signOut(context)
                            isLoading = false
                            AppMessages.show(AuthErrors.message(e, AuthErrors.Action.REGISTER), long = true)
                            if (accountCreated) onRegistered(email.trim())
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = Color.LightGray)
            Text(" or ", Modifier.padding(horizontal = 8.dp),
                color = FaceFitTextSecondary, fontSize = 12.sp)
            HorizontalDivider(Modifier.weight(1f), color = Color.LightGray)
        }
        Spacer(Modifier.height(20.dp))
        GoogleAuthButton(
            authManager = authManager,
            enabled = !isLoading && agreeToTerms,
            onLoadingChange = { isLoading = it },
            onSuccess = onRegisterSuccess
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Already registered? ", color = FaceFitTextSecondary)
            TextButton(onClick = onSignIn) {
                Text(text = "Sign in", color = FaceFitTeal, fontWeight = FontWeight.Bold)
            }
        }
    }
}
