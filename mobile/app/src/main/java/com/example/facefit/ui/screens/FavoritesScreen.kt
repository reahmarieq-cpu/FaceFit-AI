package com.example.facefit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.ui.components.FaceFitBottomNavBar
import com.example.facefit.ui.components.FavoriteFrameCard
import com.example.facefit.ui.theme.*

@Composable
fun FavoritesScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    Scaffold(
        bottomBar = {
            FaceFitBottomNavBar(currentRoute = "favorites", onNavigate = onNavigate)
        },
        containerColor = FaceFitBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBackIosNew,
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = "Favorites",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = FaceFitTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFEAEA))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "2 saved",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF7878)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            FavoriteFrameCard(
                name = "Nova",
                type = "Cat-Eye",
                material = "Rosewood - Acetate",
                iconColor = Color(0xFFD95C87)
            )
            
            FavoriteFrameCard(
                name = "Pax",
                type = "Rectangle",
                material = "Graphite - TR-90",
                iconColor = Color(0xFF333333)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FavoritesScreenPreview() {
    FacefitTheme {
        FavoritesScreen()
    }
}
