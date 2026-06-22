package app.grapheneos.pdfviewer.outline

import androidx.compose.foundation.lazy.items
import androidx.activity.compose.BackHandler
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.TestTags
import app.grapheneos.pdfviewer.ui.darkTopAppBarColors
import app.grapheneos.pdfviewer.viewModel.PdfViewModel
import kotlinx.serialization.Serializable

@Serializable
data class OutlineLevel(val nodeId: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineScreen(
    pdfViewModel: PdfViewModel,
    docTitle: String,
    onPageSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val outlineStatus by pdfViewModel.outline.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        pdfViewModel.requestOutlineIfNotAvailable()
    }

    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()

    val currentNodeId = remember(currentEntry) {
        runCatching { currentEntry?.toRoute<OutlineLevel>()?.nodeId }.getOrNull() ?: -1
    }

    val loaded = outlineStatus as? PdfViewModel.OutlineStatus.Loaded
    val subtitle = when {
        currentNodeId == -1 -> docTitle
        else -> loaded?.lookup?.get(currentNodeId)?.title?.trim() ?: docTitle
    }

    BackHandler {
        if (!navController.popBackStack()) onDismiss()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.action_outline))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close)
                        )
                    }
                },
                colors = darkTopAppBarColors()
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (val status = outlineStatus) {
                is PdfViewModel.OutlineStatus.Loaded -> {
                    if (status.outline.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.outline_not_available))
                        }
                    } else {
                        NavHost(
                            navController = navController,
                            startDestination = OutlineLevel(nodeId = -1),
                            enterTransition = {
                                slideInHorizontally(initialOffsetX = { it })
                            },
                            exitTransition = {
                                slideOutHorizontally(targetOffsetX = { -it })
                            },
                            popEnterTransition = {
                                slideInHorizontally(initialOffsetX = { -it })
                            },
                            popExitTransition = {
                                slideOutHorizontally(targetOffsetX = { it })
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable<OutlineLevel> { entry ->
                                val level = entry.toRoute<OutlineLevel>()
                                val items = if (level.nodeId == -1) {
                                    status.outline
                                } else {
                                    status.lookup[level.nodeId]?.children.orEmpty()
                                }
                                OutlineLevelList(
                                    nodes = items,
                                    onItemClick = { onPageSelected(it.pageNumber) },
                                    onChildrenClick = {
                                        navController.navigate(OutlineLevel(it.id))
                                    }
                                )
                            }
                        }
                    }
                }
                is PdfViewModel.OutlineStatus.NoOutline -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.outline_not_available))
                    }
                }
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlineLevelList(
    nodes: List<OutlineNode>,
    onItemClick: (OutlineNode) -> Unit,
    onChildrenClick: (OutlineNode) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(nodes, key = { it.id }) { node ->
            OutlineItem(
                node = node,
                onItemClick = { onItemClick(node) },
                onChildrenClick = { onChildrenClick(node) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun OutlineItem(
    node: OutlineNode,
    onItemClick: () -> Unit,
    onChildrenClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(TestTags.OUTLINE_ITEM),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = node.pageNumber.toString(),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (node.children.isNotEmpty()) {
            IconButton(onClick = onChildrenClick) {
                Icon(
                    painterResource(R.drawable.ic_navigate_next_24dp),
                    contentDescription = stringResource(R.string.outline_child_button_description)
                )
            }
        }
    }
}
