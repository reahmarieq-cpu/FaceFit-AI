package com.example.facefit.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.logic.AuthManager
import com.example.facefit.ui.components.FaceFitButton
import com.example.facefit.ui.components.FaceFitLogo
import com.example.facefit.ui.components.FaceFitTextField
import com.example.facefit.ui.theme.FaceFitTeal
import com.example.facefit.ui.theme.FaceFitTextPrimary
import com.example.facefit.ui.theme.FaceFitTextSecondary
import com.example.facefit.ui.theme.FacefitTheme
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(
    authManager: AuthManager,
    onBack: () -> Unit,
    onSignInSuccess: () -> Unit,
    onRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
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
            text = "Welcome back",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = FaceFitTextPrimary
        )
        Text(
            text = "Sign in to continue to FaceFit AI",
            fontSize = 14.sp,
            color = FaceFitTextSecondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        FaceFitTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            leadingIcon = Icons.Outlined.Email
        )

        Spacer(modifier = Modifier.height(16.dp))

        FaceFitTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            leadingIcon = Icons.Outlined.Lock,
            isPassword = true
        )

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = { /* Forgot Password */ }) {
                Text(text = "Forgot password?", color = FaceFitTeal, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = FaceFitTeal)
        } else {
            FaceFitButton(
                text = "Sign in",
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
                        return@FaceFitButton
                    }
                    
                    isLoading = true
                    scope.launch {
                        val result = authManager.signIn(email, password)
                        isLoading = false
                        result.onSuccess {
                            onSignInSuccess()
                        }.onFailure { e ->
                            Toast.makeText(context, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = Color.LightGray)
            Text(
                text = " or ",
                modifier = Modifier.padding(horizontal = 8.dp),
                color = FaceFitTextSecondary,
                fontSize = 12.sp
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = Color.LightGray)
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = { /* Google Sign In */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = FaceFitTextPrimary)
        ) {
            Text(text = "G Continue with Google", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "No account? ", color = FaceFitTextSecondary)
            TextButton(onClick = onRegister) {
                Text(text = "Register", color = FaceFitTeal, fontWeight = FontWeight.Bold)
            }
        }
    }
}
