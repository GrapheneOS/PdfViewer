package app.grapheneos.pdfviewer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.grapheneos.pdfviewer.R

/**
 * Startup screen which includes an Open button.
 */
@Composable
fun StartupScreen(
    modifier: Modifier,
    onOpenPdfButtonClicked: () -> Unit,
    onLaunchedEffect: () -> Unit,
) {
    LaunchedEffect(key1 = Unit) {
        onLaunchedEffect()
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.requiredSize(300.dp)
            )
        }

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge
        )
        FilledTonalButton(
            modifier = modifier.fillMaxWidth(),
            onClick = {
                onOpenPdfButtonClicked()
            }
        ) {
            Icon(
                imageVector = Icons.Filled.FileOpen,
                contentDescription = null
            )
            Spacer(modifier = modifier.width(8.dp))
            Text(stringResource(R.string.open))
        }
    }
}
