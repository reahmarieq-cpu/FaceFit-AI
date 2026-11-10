package com.example.facefit.navigation

import com.example.facefit.ui.auth.RegisterScreen
import com.example.facefit.ui.auth.SignInScreen
import com.example.facefit.ui.catalog.FrameDetailsScreen
import com.example.facefit.ui.catalog.FrameMatchesScreen
import com.example.facefit.ui.catalog.sampleFrames
import com.example.facefit.ui.dashboard.DashboardScreen
import com.example.facefit.ui.favorites.FavoritesScreen
import com.example.facefit.ui.profile.ProfileScreen
import com.example.facefit.ui.scan.ScanResultsScreen
import com.example.facefit.ui.scan.ScanScreen
import android.net.Uri
import com.example.facefit.ui.shops.ShopDetailsScreen
import com.example.facefit.ui.shops.NearbyShopsScreen
import com.example.facefit.ui.tryon.VirtualTryOnScreen
import com.example.facefit.ui.welcome.OnboardingScreen
import com.example.facefit.ui.welcome.SplashScreen

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.facefit.data.auth.AuthManager
import kotlinx.coroutines.launch

@Composable
fun FaceFitNavHost(
    authManager: AuthManager,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    var latestScanMetrics by remember { mutableStateOf<com.example.facefit.facefit.analysis.FaceAnalyzer.FaceMetrics?>(null) }
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
        modifier = modifier
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
                onSignIn = { navController.navigate("signin") }
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
                            onRequestCameraPermission()
                        }
                    } else {
                        navController.navigate(route)
                    }
                }
            )
        }
        composable("nearbyShops") {
            NearbyShopsScreen(onBack = { navController.popBackStack() }, onNavigate = { route ->
                if (route == "scan" && !hasCameraPermission) onRequestCameraPermission()
                else navController.navigate(route) { launchSingleTop = true }
            }, onShopClick = { shopId ->
                navController.navigate("shopDetails/${Uri.encode(shopId)}") { launchSingleTop = true }
            })
        }
        composable("shopDetails/{shopId}") { entry ->
            ShopDetailsScreen(shopId = entry.arguments?.getString("shopId").orEmpty(),
                onBack = { navController.popBackStack() })
        }
        composable("profile") {
            ProfileScreen(
                authManager = authManager,
                onNavigate = { route ->
                    if (route == "scan") {
                        if (hasCameraPermission) {
                            navController.navigate("scan")
                        } else {
                            onRequestCameraPermission()
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
                savedFrames=savedFrameNames,
                onFavoriteChange=updateFrameFavorite,
                onFrameClick={ frame -> navController.navigate("frameDetails/${frame.name}") },
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    if (route == "scan") {
                        if (hasCameraPermission) {
                            navController.navigate("scan")
                        } else {
                            onRequestCameraPermission()
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
                            faceShape = metrics.faceShape,
                            shapeMeasurements = metrics.shapeMeasurements,
                            shapeSampleCount = metrics.shapeSampleCount
                        )

                        if (result.isSuccess) {
                            Toast.makeText(
                                context,
                                "Face scan saved",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
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
            val metrics = latestScanMetrics ?: com.example.facefit.facefit.analysis.FaceAnalyzer.FaceMetrics(
                foreheadWidth = 140f,
                jawline = 120f,
                eyeSpacing = 65f,
                faceShape = "Oval",
                isFaceDetected = true
            )
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
        }
        composable("frameDetails/{frameName}") { entry ->
            sampleFrames.find { it.name == entry.arguments?.getString("frameName") }?.let { frame ->
                FrameDetailsScreen(
                    frame = frame,
                    isFavorite = frame.name in savedFrameNames,
                    onBack = { navController.popBackStack() },
                    onFavoriteChange = { saved -> updateFrameFavorite(frame.name, saved) },
                    onPreviewModel = { navController.navigate("modelPreview/${frame.name}") },
                    onStartTryOn = { navController.navigate("virtualTryOn/${frame.name}") }
                )
            }
        }
        composable("modelPreview/{frameName}") { entry ->
            sampleFrames.find { it.name == entry.arguments?.getString("frameName") }?.let { frame ->
                com.example.facefit.ui.catalog.ModelPreviewScreen(
                    frame = frame, onBack = { navController.popBackStack() })
            }
        }
        composable("virtualTryOn/{frameName}") { entry ->
            sampleFrames.find { it.name == entry.arguments?.getString("frameName") }?.let { frame ->
                VirtualTryOnScreen(frame = frame, onBack = { navController.popBackStack() })
            }
        }
    }
}
