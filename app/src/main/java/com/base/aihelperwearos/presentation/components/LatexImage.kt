package com.base.aihelperwearos.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

import androidx.compose.ui.res.stringResource
import com.base.aihelperwearos.R

...

@Composable
fun LatexImage(
    latex: String,
    imageUrl: String,
    isDisplayMode: Boolean,
    modifier: Modifier = Modifier
) {
    var showFullscreen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (isDisplayMode) 4.dp else 2.dp),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(300)
                .build(),
            contentDescription = stringResource(R.string.formula_description, latex),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clickable { showFullscreen = true },
            loading = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        indicatorColor = MaterialTheme.colors.primary
                    )
                    Text(
                        text = stringResource(R.string.loading),
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            },
            success = {
                Image(
                    painter = it.painter,
                    contentDescription = stringResource(R.string.formula_description, latex),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .background(Color.White)
                        .padding(4.dp)
                )
            },
            error = {
                Text(
                    text = stringResource(R.string.formula_error, latex),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colors.error,
                    modifier = Modifier
                        .background(Color(0xFF2A2A2A))
                        .padding(8.dp)
                )
            }
        )
    }

    if (showFullscreen) {
        LatexFullscreenDialog(
            imageUrl = imageUrl,
            latex = latex,
            onDismiss = { showFullscreen = false }
        )
    }
}

@Composable
private fun LatexFullscreenDialog(
    imageUrl: String,
    latex: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += offsetChange
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(300)
                    .build(),
                contentDescription = stringResource(R.string.zoomed_formula_description, latex),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize(0.9f)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(state = state)
                    .background(Color.White)
                    .padding(8.dp),
                loading = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        indicatorColor = Color.White
                    )
                },
                error = {
                    Text(
                        text = stringResource(R.string.formula_error, latex),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Red,
                        modifier = Modifier
                            .background(Color.White)
                            .padding(16.dp)
                    )
                }
            )

            Text(
                text = stringResource(R.string.zoom_and_close_hint, (scale * 100).toInt()),
                fontSize = 10.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
