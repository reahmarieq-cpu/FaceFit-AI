package com.example.facefit

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.facefit.logic.AuthManager
import com.example.facefit.ui.screens.*
import com.example.facefit.ui.theme.FacefitTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val authManager by lazy { AuthManager() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FacefitTheme {
                val navController = rememberNavController()
                
                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasCameraPermission = isGranted
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val coroutineScope = rememberCoroutineScope()
                    var latestScanMetrics by remember { mutableStateOf<com.example.facefit.logic.FaceAnalyzer.FaceMetrics?>(null) }
                    var savedFrameNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
                    val updateFrameFavorite: (String, Boolean) -> Unit = { name, saved ->
                        savedFrameNames = if (saved) (savedFrameNames + name).distinct() else savedFrameNames - name
                    }
                    val startDest = try {
                        if (authManager.currentUser != null) "dashboard" else "splash"
                    } catch (e: Exception) {
                        "splash"
                    }
                    
                    NavHost(
                        navController = navController,
                        startDestination = startDest,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("splash") {
                            SplashScreen(
                                onGetStarted = { navController.navigate("onboarding") },
                                onSignIn = { navController.navigate("signin") }
                            )
                        }
                        composable("onboarding") {
                            OnboardingScreen(
                                onCreateAccount = { navController.navigate("register") },
                                onSignIn = { navController.navigate("signin") },
                                onFaceScan = {
                                    if (hasCameraPermission) {
                                        navController.navigate("scan")
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            )
                        }
                        composable("signin") {
                            SignInScreen(
                                authManager = authManager,
                                onBack = { navController.popBackStack() },
                                onSignInSuccess = { 
                                    navController.navigate("dashboard") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                },
                                onRegister = { navController.navigate("register") }
                            )
                        }
                        composable("register") {
                            RegisterScreen(
                                authManager = authManager,
                                onBack = { navController.popBackStack() },
                                onRegisterSuccess = { 
                                    navController.navigate("dashboard") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                },
                                onSignIn = { navController.navigate("signin") }
                            )
                        }
                        composable("dashboard") {
                            DashboardScreen(
                                authManager = authManager,
                                userName = authManager.currentUser?.email ?: "User",
                                onNavigate = { route ->
                                    if (route == "scan") {
                                        if (hasCameraPermission) {
                                            navController.navigate("scan")
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    } else {
                                        navController.navigate(route)
                                    }
                                }
                            )
                        }
                        composable("profile") {
                            ProfileScreen(
                                authManager = authManager,
                                onNavigate = { route ->
                                    if (route == "scan") {
                                        if (hasCameraPermission) {
                                            navController.navigate("scan")
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    } else {
                                        navController.navigate(route)
                                    }
                                },
                                onSignOut = {
                                    navController.navigate("splash") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("favorites") {
                            FavoritesScreen(
                                onBack = { navController.popBackStack() },
                                onNavigate = { route ->
                                    if (route == "scan") {
                                        if (hasCameraPermission) {
                                            navController.navigate("scan")
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    } else {
                                        navController.navigate(route)
                                    }
                                }
                            )
                        }
                        composable("scan") {
                            ScanScreen(
                                onBack = { navController.popBackStack() },
                                onComplete = { metrics ->
                                    latestScanMetrics = metrics
                                    coroutineScope.launch {
                                        val result = authManager.saveFaceScan(
                                            foreheadWidth = metrics.foreheadWidth,
                                            jawline = metrics.jawline,
                                            eyeSpacing = metrics.eyeSpacing,
                                            faceShape = metrics.faceShape
                                        )

                                        if (result.isSuccess) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Face scan saved",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                this@MainActivity,
                                                result.exceptionOrNull()?.message ?: "Face scan was not saved",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }

                                        navController.navigate("scanResults")
                                    }
                                }
                            )
                        }
                        composable("scanResults") {
                            latestScanMetrics?.let { metrics ->
                                ScanResultsScreen(
                                    metrics = metrics,
                                    onNext = { navController.navigate("frameMatches") { launchSingleTop = true } },
                                    onBack = { navController.navigate("dashboard") },
                                    onScanAgain = {
                                        latestScanMetrics = null
                                        navController.navigate("scan") {
                                            popUpTo("scanResults") { inclusive = true }
                                        }
                                    }
                                )
                            } ?: DashboardScreen(
                                authManager = authManager,
                                userName = authManager.currentUser?.email ?: "User",
                                onNavigate = { route -> navController.navigate(route) }
                            )
                        }
                        composable("frameMatches") {
                            latestScanMetrics?.let { metrics ->
                                FrameMatchesScreen(
                                    metrics = metrics,
                                    savedFrames = savedFrameNames,
                                    onFavoriteChange = updateFrameFavorite,
                                    onFrameClick = { frame -> navController.navigate("frameDetails/${frame.name}") },
                                    onBack = { navController.popBackStack() },
                                    onScanAgain = {
                                        latestScanMetrics = null
                                        navController.navigate("scan") {
                                            popUpTo("scan") { inclusive = true }
                                        }
                                    }
                                )
                            } ?: DashboardScreen(
                                authManager = authManager,
                                userName = authManager.currentUser?.email ?: "User",
                                onNavigate = { route -> navController.navigate(route) }
                            )
                        }
                        composable("frameDetails/{frameName}") { entry ->
                            sampleFrames.find { it.name == entry.arguments?.getString("frameName") }?.let { frame ->
                                FrameDetailsScreen(
                                    frame = frame,
                                    isFavorite = frame.name in savedFrameNames,
                                    onBack = { navController.popBackStack() },
                                    onFavoriteChange = { saved -> updateFrameFavorite(frame.name, saved) },
                                    onStartTryOn = { navController.navigate("virtualTryOn/${frame.name}") }
                                )
                            }
                        }
                        composable("virtualTryOn/{frameName}") { entry ->
                            sampleFrames.find { it.name == entry.arguments?.getString("frameName") }?.let { frame ->
                                VirtualTryOnScreen(frame = frame, onBack = { navController.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
    }
}
