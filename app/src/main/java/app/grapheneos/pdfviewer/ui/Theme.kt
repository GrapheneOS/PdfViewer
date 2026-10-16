package app.grapheneos.pdfviewer.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun PdfViewerTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) darkColorScheme() else lightColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
private fun darkScheme(): ColorScheme =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        darkColorScheme()
    }

@Composable
fun darkTopAppBarColors(): TopAppBarColors {
    val darkScheme = darkScheme()
    return TopAppBarDefaults.topAppBarColors(
        containerColor = darkScheme.surface,
        titleContentColor = darkScheme.onSurface,
        navigationIconContentColor = darkScheme.onSurfaceVariant,
        actionIconContentColor = darkScheme.onSurfaceVariant
    )
}

/**
 * Text field colours for a field sitting inside a [darkTopAppBarColors] bar. Without these the
 * field inherits the ambient (possibly light) scheme and renders dark text on a dark bar.
 */
@Composable
fun darkSearchFieldColors(): TextFieldColors {
    val darkScheme = darkScheme()
    return TextFieldDefaults.colors(
        focusedTextColor = darkScheme.onSurface,
        unfocusedTextColor = darkScheme.onSurface,
        cursorColor = darkScheme.primary,
        focusedPlaceholderColor = darkScheme.onSurfaceVariant,
        unfocusedPlaceholderColor = darkScheme.onSurfaceVariant,
        focusedTrailingIconColor = darkScheme.onSurfaceVariant,
        unfocusedTrailingIconColor = darkScheme.onSurfaceVariant,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
    )
}
