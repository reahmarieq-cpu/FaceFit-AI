package com.example.facefit

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.ui.favorites.FavoritesScreen
import com.example.facefit.ui.catalog.FrameMatchesScreen
import com.example.facefit.ui.theme.FacefitTheme
import org.junit.Rule
import org.junit.Test

class FavoritesFlowTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun catalogHeartAppearsInFavoritesAndRemovalUpdatesCatalog() {
        val saved=mutableStateOf(emptyList<String>())
        val favoritesVisible=mutableStateOf(false)
        val update:(String,Boolean)->Unit={name,keep->
            saved.value=if(keep) (saved.value+name).distinct() else saved.value-name
        }
        compose.setContent {
            FacefitTheme {
                if(favoritesVisible.value) FavoritesScreen(savedFrames=saved.value,onFavoriteChange=update)
                else FrameMatchesScreen(FaceAnalyzer.FaceMetrics(140f,120f,65f,"Oval",isFaceDetected=true),
                    onBack={},onScanAgain={},savedFrames=saved.value,onFavoriteChange=update,onFrameClick={})
            }
        }
        compose.onNodeWithContentDescription("Save Pax").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Unsave Pax").assertExists()
        compose.runOnIdle { favoritesVisible.value=true }
        compose.onNodeWithText("1 saved").assertExists()
        compose.onNodeWithText("Pax").assertExists()
        compose.onNodeWithContentDescription("Unsave Pax").performClick()
        compose.onNodeWithText("0 saved").assertExists()
        compose.onNodeWithText("No favorite frames yet").assertExists()
        compose.runOnIdle { favoritesVisible.value=false }
        compose.onNodeWithContentDescription("Save Pax").performScrollTo().assertExists()
    }
}
