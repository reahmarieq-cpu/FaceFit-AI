package com.example.facefit.ui.favorites

import com.example.facefit.ui.catalog.FrameIllustration
import com.example.facefit.ui.catalog.SampleFrame
import com.example.facefit.ui.catalog.sampleFrames
import com.example.facefit.ui.components.ResultIconButton

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.ui.components.FaceFitBottomNavBar
import com.example.facefit.ui.theme.*

@Composable
fun FavoritesScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    savedFrames: List<String> = emptyList(),
    onFavoriteChange: (String,Boolean) -> Unit = {_,_->},
    onFrameClick: (SampleFrame) -> Unit = {}
) {
    val favorites=sampleFrames.filter { it.name in savedFrames }
    Scaffold(bottomBar={ FaceFitBottomNavBar("favorites",onNavigate) },containerColor=FaceFitBackground) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(24.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    ResultIconButton(onBack) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,"Back")
                    }
                    Text("Favorites",Modifier.weight(1f).padding(start=16.dp),fontSize=24.sp,
                        fontWeight=FontWeight.Bold,color=FaceFitTextPrimary)
                    Surface(color=Color(0xFFFFEAEA),shape=RoundedCornerShape(12.dp)) {
                        Text("${favorites.size} saved",Modifier.padding(horizontal=12.dp,vertical=6.dp),
                            fontSize=12.sp,fontWeight=FontWeight.Bold,color=Color(0xFFFF7878))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if(favorites.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical=40.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.FavoriteBorder,null,tint=FaceFitTeal,modifier=Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No favorite frames yet",fontWeight=FontWeight.SemiBold,color=FaceFitTextPrimary)
                    Text("Tap a heart in the frame catalog to save a frame here.",
                        modifier=Modifier.padding(top=8.dp),fontSize=14.sp,color=FaceFitTextSecondary)
                    TextButton(onClick={onNavigate("frameMatches")}) { Text("Browse frames") }
                }
            }
            items(favorites,key={it.name}) { frame ->
                Card(modifier=Modifier.fillMaxWidth().softContainerShadow(RoundedCornerShape(24.dp))
                    .clickable(onClickLabel="View ${frame.name} details") { onFrameClick(frame) },
                    shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(76.dp).background(frame.color.copy(alpha=.1f),RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center) {
                            FrameIllustration(frame,Modifier.fillMaxWidth().height(48.dp).padding(horizontal=6.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(frame.name,fontSize=17.sp,fontWeight=FontWeight.Bold,color=FaceFitTextPrimary)
                            Text(frame.category,fontSize=12.sp,color=FaceFitTextSecondary)
                            Spacer(Modifier.height(4.dp))
                            Text("${frame.colorName} - ${frame.material}",fontSize=12.sp,color=FaceFitTextSecondary)
                        }
                        IconButton(onClick={onFavoriteChange(frame.name,false)}) {
                            Icon(Icons.Filled.Favorite,"Unsave ${frame.name}",tint=Color(0xFFFF7878))
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground=true)
@Composable
fun FavoritesScreenPreview() {
    FacefitTheme { FavoritesScreen(savedFrames=listOf("Nova","Pax")) }
}
