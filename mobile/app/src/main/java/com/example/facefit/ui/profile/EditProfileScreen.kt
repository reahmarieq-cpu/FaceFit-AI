package com.example.facefit.ui.profile

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facefit.data.auth.AuthManager
import com.example.facefit.ui.theme.FaceFitTeal
import com.example.facefit.ui.theme.FaceFitTealVariant
import com.example.facefit.ui.theme.FaceFitTextPrimary
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun EditProfileScreen(
    authManager: AuthManager,
    profile: Map<String, Any>,
    onBack: () -> Unit,
    onSaved: (Map<String, Any>) -> Unit
) {
    var fullName by rememberSaveable { mutableStateOf(profile.profileFullName()) }
    var phone by rememberSaveable { mutableStateOf((profile["phone"] as? String).orEmpty()) }
    var age by rememberSaveable { mutableStateOf(profile["age"]?.toString().orEmpty()) }
    var gender by rememberSaveable { mutableStateOf((profile["gender"] as? String).orEmpty()) }
    var selectedPhoto by rememberSaveable { mutableStateOf<ByteArray?>(null) }
    var genderMenuOpen by remember { mutableStateOf(false) }
    var loadingPhoto by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editable = !saving && !loadingPhoto
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            loadingPhoto = true
            error = null
            scope.launch {
                try {
                    selectedPhoto = withContext(Dispatchers.IO) { loadAvatar(context.contentResolver, uri) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    error = "Could not open that photo. Choose another one."
                } finally {
                    loadingPhoto = false
                }
            }
        }
    }

    BackHandler { if (!saving) onBack() }

    Column(
        modifier = Modifier.fillMaxSize().background(FaceFitTealVariant)
            .safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledIconButton(
                onClick = onBack,
                enabled = !saving,
                shape = RoundedCornerShape(14.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White, contentColor = FaceFitTextPrimary
                )
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to Profile")
            }
            Text("Edit Profile", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = FaceFitTextPrimary)
        }

        Spacer(Modifier.height(20.dp))
        Box(contentAlignment = Alignment.BottomEnd) {
            val photo = selectedPhoto
            val avatarBytes = remember(photo, profile) { photo ?: (profile["profilePhoto"] as? Blob)?.toBytes() }
            ProfileAvatar(
                fullName = fullName,
                photo = avatarBytes,
                modifier = Modifier.size(84.dp).shadow(8.dp, RoundedCornerShape(24.dp))
            )
            FilledIconButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = editable,
                modifier = Modifier.offset(x = 7.dp, y = 12.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = FaceFitTeal, contentColor = Color.White
                ),
                shape = CircleShape
            ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = "Change profile photo", modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileField("Full name", fullName, { fullName = it }, enabled = editable,
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            ProfileField("Email", authManager.currentUser?.email ?: (profile["email"] as? String).orEmpty(), {},
                readOnly = true)
            ProfileField("Phone", phone, { phone = it }, enabled = editable,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileField("Age", age, { if (it.length <= 3 && it.all { c -> c in '0'..'9' }) age = it },
                    modifier = Modifier.width(72.dp), enabled = editable,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Gender", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FaceFitTextPrimary)
                    Box {
                        OutlinedButton(
                            onClick = { genderMenuOpen = true },
                            enabled = editable,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = null,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White, contentColor = FaceFitTextPrimary
                            )
                        ) {
                            Text(gender.ifBlank { "Select" }, modifier = Modifier.weight(1f))
                            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = genderMenuOpen, onDismissRequest = { genderMenuOpen = false }) {
                            listOf("", "Female", "Male", "Non-binary", "Prefer not to say").forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.ifBlank { "Not specified" }) },
                                    onClick = {
                                        gender = option
                                        genderMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    if (!editable) return@Button
                    val ageYears = age.toIntOrNull()
                    if (age.isNotEmpty() && (ageYears == null || ageYears !in 0..125)) {
                        error = "Enter an age from 0 to 125 or leave it blank."
                        return@Button
                    }

                    saving = true
                    error = null
                    scope.launch {
                        authManager.updateProfile(fullName, phone, ageYears, gender, selectedPhoto)
                            .onSuccess(onSaved)
                            .onFailure {
                                val code = (it as? FirebaseFirestoreException)?.code
                                error = if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                    "Your account does not have permission to update this profile."
                                } else {
                                    "Could not save your profile. Check your connection and try again."
                                }
                            }
                        saving = false
                    }
                },
                enabled = editable && fullName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FaceFitTeal, contentColor = Color.White),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Text(if (saving) "Saving…" else if (loadingPhoto) "Opening photo…" else "Save changes",
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    keyboard: KeyboardOptions = KeyboardOptions.Default
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FaceFitTextPrimary)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = enabled,
            readOnly = readOnly,
            keyboardOptions = keyboard,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White,
                disabledContainerColor = Color.White,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = FaceFitTeal
            )
        )
    }
}

internal fun Map<String, Any>.profileFullName(): String =
    listOf("firstName", "lastName").mapNotNull { this[it] as? String }
        .filter { it.isNotBlank() }.joinToString(" ")

@Composable
internal fun ProfileAvatar(fullName: String, photo: ByteArray?, modifier: Modifier = Modifier) {
    val bitmap = remember(photo) {
        photo?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    val words = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val initials = (words.firstOrNull()?.take(1).orEmpty() +
        words.lastOrNull()?.takeIf { words.size > 1 }?.take(1).orEmpty()).uppercase()
    Box(
        modifier = modifier.clip(RoundedCornerShape(24.dp)).background(
            Brush.verticalGradient(listOf(Color(0xFF167EA9), FaceFitTeal))
        ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else {
            Text(initials.ifBlank { "–" }, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

private fun loadAvatar(resolver: ContentResolver, uri: Uri): ByteArray {
    val maxSide = 256
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > maxSide) {
                decoder.setTargetSize(
                    (info.size.width.toLong() * maxSide / longest).toInt().coerceAtLeast(1),
                    (info.size.height.toLong() * maxSide / longest).toInt().coerceAtLeast(1)
                )
            }
        }
    } else {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        var sample = 1
        val longest = maxOf(options.outWidth, options.outHeight)
        while (longest / sample > maxSide * 2) sample *= 2
        options.inJustDecodeBounds = false
        options.inSampleSize = sample
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Photo could not be decoded")
    }
    val longest = maxOf(bitmap.width, bitmap.height)
    val thumbnail = if (longest > maxSide) {
        Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * maxSide / longest).coerceAtLeast(1),
            (bitmap.height * maxSide / longest).coerceAtLeast(1), true
        )
    } else bitmap
    return ByteArrayOutputStream().use { bytes ->
        check(thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, bytes))
        bytes.toByteArray()
    }
}
