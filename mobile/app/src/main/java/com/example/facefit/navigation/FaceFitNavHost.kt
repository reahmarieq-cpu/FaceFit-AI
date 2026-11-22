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
import com.example.facefit.ui.shops.ShopProductDetailsScreen
import com.example.facefit.ui.shops.ShopDetailsScreen
import com.example.facefit.ui.shops.NearbyShopsScreen
import com.example.facefit.ui.tryon.VirtualTryOnScreen
import com.example.facefit.ui.welcome.OnboardingScreen
import com.example.facefit.ui.welcome.SplashScreen

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import com.example.facefit.data.favorites.FavoritesRepository
import com.example.facefit.data.session.UserPreferencesRepository
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
    modifier: Modifier = Modifier,
    showStartup: Boolean = false,
    startupReady: Boolean = true,
    onStartupFinished: () -> Unit = {},
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val scanPreferences = remember(context) { UserPreferencesRepository(context) }
    val latestScanFlow = remember(scanPreferences) { scanPreferences.observeLatestScan() }
    val latestScan by latestScanFlow.collectAsState(UserPreferencesRepository.LatestScan())
    var latestScanMetrics by remember(latestScan.userId) {
        mutableStateOf<com.example.facefit.facefit.analysis.FaceAnalyzer.FaceMetrics?>(null)
    }
    val availableScanMetrics = latestScanMetrics ?: latestScan.metrics
    val favoritesRepository = remember { FavoritesRepository() }
    val favoritesFlow = remember(favoritesRepository) { favoritesRepository.observeFavorites() }
    val favorites by favoritesFlow.collectAsState(FavoritesRepository.Favorites())
    val savedFrameNames = favorites.frames.map { it.name }
    LaunchedEffect(favorites.error) {
        favorites.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    val updateFrameFavorite: (String, Boolean) -> Unit = { name, saved ->
        val frame = favorites.frames.firstOrNull { it.name == name }
            ?: sampleFrames.firstOrNull { it.name == name }
        if (frame != null) favoritesRepository.setFavorite(frame, saved) { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    val startDest = remember {
        if (showStartup) "animatedSplash"
        else if (authManager.currentUser != null) "dashboard" else "splash"
    }

    NavHost(
        navController = navController,
        startDestination = startDest,
        modifier = modifier
    ) {
        composable("animatedSplash", exitTransition = { fadeOut(tween(200)) }) {
            com.example.facefit.ui.splash.SplashScreen(
                start = startupReady,
                skipAnimation = !showStartup,
                onNavigateNext = { isLoggedIn ->
                    if (navController.currentDestination?.route != "animatedSplash") return@SplashScreen
                    navController.navigate(if (isLoggedIn) "dashboard" else "splash") {
                        popUpTo("animatedSplash") { inclusive = true }
                        launchSingleTop = true
                    }
                    onStartupFinished()
                },
            )
        }
        composable("splash", enterTransition = {
            if (initialState.destination.route == "animatedSplash") fadeIn(tween(200)) else null
        }) {
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
        composable("dashboard", enterTransition = {
            if (initialState.destination.route == "animatedSplash") fadeIn(tween(200)) else null
        }) {
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
            val shopId = entry.arguments?.getString("shopId").orEmpty()
            ShopDetailsScreen(shopId = shopId, onBack = { navController.popBackStack() },
                onFrameClick = { key ->
                    navController.navigate("shopProduct/${Uri.encode(shopId)}/${Uri.encode(key)}") { launchSingleTop = true }
                })
        }
        composable("shopProduct/{shopId}/{frameKey}") { entry ->
            val shopId = entry.arguments?.getString("shopId").orEmpty()
            ShopProductDetailsScreen(shopId = shopId, frameKey = entry.arguments?.getString("frameKey").orEmpty(),
                onBack = { navController.popBackStack() }, onViewShop = {
                    val route = "shopDetails/${Uri.encode(shopId)}"
                    if (!navController.popBackStack(route, false)) navController.navigate(route) { launchSingleTop = true }
                })
        }
        composable("profile") {
            ProfileScreen(
                authManager = authManager,
                savedCount = favorites.frames.size,
                recentFaceShape = latestScan.faceShape,
                favoritesLoading = favorites.loading,
                favoritesError = favorites.error,
                onNavigateToResults = {
                    if (availableScanMetrics != null) {
                        navController.navigate("scanResults") { launchSingleTop = true }
                    } else {
                        Toast.makeText(context, "Scan again to restore your full analysis.", Toast.LENGTH_LONG).show()
                        if (hasCameraPermission) navController.navigate("scan") else onRequestCameraPermission()
                    }
                },
                onNavigateToScan = {
                    if (hasCameraPermission) navController.navigate("scan") else onRequestCameraPermission()
                },
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
        composable("privacySecurity") {
            com.example.facefit.ui.profile.PrivacySecurityScreen(
                onBack = { navController.popBackStack() },
                onScanCleared = { latestScanMetrics = null },
                onAccountDeleted = { navController.navigate("splash") { popUpTo(0) { inclusive = true } } },
            )
        }
        composable("favorites") {
            FavoritesScreen(
                frames = favorites.frames,
                loading = favorites.loading,
                error = favorites.error,
                signedIn = favorites.userId != null,
                onFavoriteChange=updateFrameFavorite,
                onFrameClick={ frame -> navController.navigate("frameDetails/${Uri.encode(frame.name)}") },
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
                    scanPreferences.saveLatestScan(metrics)
                    coroutineScope.launch {
                        if (!scanPreferences.isHistoryEnabled()) {
                            navController.navigate("scanResults")
                            return@launch
                        }
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
            availableScanMetrics?.let { metrics ->
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
            val metrics = availableScanMetrics ?: com.example.facefit.facefit.analysis.FaceAnalyzer.FaceMetrics(
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
                onFrameClick = { frame -> navController.navigate("frameDetails/${Uri.encode(frame.name)}") },
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
            (sampleFrames + favorites.frames).find { it.name == entry.arguments?.getString("frameName") }?.let { frame ->
                FrameDetailsScreen(
                    frame = frame,
                    isFavorite = frame.name in savedFrameNames,
                    onBack = { navController.popBackStack() },
                    onFavoriteChange = { saved -> updateFrameFavorite(frame.name, saved) },
                    onPreviewModel = { navController.navigate("modelPreview/${Uri.encode(frame.name)}") },
                    onStartTryOn = { navController.navigate("virtualTryOn/${Uri.encode(frame.name)}") }
                )
            }
        }
        composable("modelPreview/{frameName}") { entry ->
            (sampleFrames + favorites.frames).find { it.name == entry.arguments?.getString("frameName") }?.let { frame ->
                com.example.facefit.ui.catalog.ModelPreviewScreen(
                    frame = frame, onBack = { navController.popBackStack() })
            }
        }
        composable("virtualTryOn/{frameName}") { entry ->
            (sampleFrames + favorites.frames).find { it.name == entry.arguments?.getString("frameName") }?.let { frame ->
                VirtualTryOnScreen(
                    frame = frame,
                    onBack = { navController.popBackStack() },
                    isFavorite = frame.name in savedFrameNames,
                    onFavoriteChange = { saved -> updateFrameFavorite(frame.name, saved) },
                    onFindNearbyShops = {
                        navController.navigate("nearbyShops") { launchSingleTop = true }
                    },
                )
            }
        }
    }
}
