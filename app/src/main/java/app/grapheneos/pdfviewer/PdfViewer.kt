package app.grapheneos.pdfviewer

import android.content.Intent
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.grapheneos.pdfviewer.loader.DocumentProperty
import app.grapheneos.pdfviewer.ui.PdfViewerScreen
import app.grapheneos.pdfviewer.ui.StartupScreen
import app.grapheneos.pdfviewer.ui.mimeTypePdf
import app.grapheneos.pdfviewer.viewModel.PdfViewModel
import kotlinx.coroutines.launch
import java.io.IOException

private const val MIN_WEBVIEW_RELEASE = 92

enum class PdfViewerScreens(@StringRes val title: Int) {
    Start(title = R.string.start),
    PdfViewer(title = R.string.app_name),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerAppBar(
    currentScreen: PdfViewerScreens,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,

    pdfUiState: PdfViewModel.PdfUiState,

    onPreviousPageButtonClicked: () -> Unit,
    onNextPageButtonClicked: () -> Unit,

    dropDownMenuShown: Boolean,
    onDropDownMenuButtonClicked: () -> Unit,
    onDropDownMenuDismissRequest: () -> Unit,

    onOpenDocumentDropdownMenuItemClicked: () -> Unit,

    onFirstPageDropdownMenuItemClicked: () -> Unit,

    onLastPageDropdownMenuItemClicked: () -> Unit,

    jumpToPageDialogShown: Boolean,
    onJumpToPageDialogDismissRequest: () -> Unit,
    onJumpToPageDropdownMenuItemClicked: () -> Unit,
    jumpToPageDialogContent: @Composable () -> Unit,
    jumpToPageDialogDismissButton: @Composable () -> Unit,
    jumpToPageDialogConfirmButton: @Composable () -> Unit,

    onRotateClockwiseDropdownMenuItemClicked: () -> Unit,

    onRotateCounterclockwiseDropdownMenuItemClicked: () -> Unit,

    onShareDropdownMenuItemClicked: () -> Unit,

    onSaveAsDropdownMenuItemClicked: () -> Unit,

    onToggleTextLayerVisibilityDropdownMenuItemClicked: () -> Unit,

    propertiesDialogShown: Boolean,
    onPropertiesDialogDismissRequest: () -> Unit,
    onViewDocumentPropertiesDropdownMenuItemClicked: () -> Unit,
    propertiesDialogContent: @Composable () -> Unit,
    propertiesDialogConfirmButton: @Composable () -> Unit,

    passwordPromptDialogShown: Boolean,
    onPasswordPromptDialogDismissRequest: () -> Unit,
    passwordPromptDialogContent: @Composable () -> Unit,
    passwordPromptDialogDismissButton: @Composable () -> Unit,
    passwordPromptDialogConfirmButton: @Composable () -> Unit,

    updateWebViewDialogShown: Boolean,
    updateWebViewDialogContent: @Composable () -> Unit,

    windowInsets: WindowInsets,

    modifier: Modifier
) {
    val dropDownMenuItemTextStyle = typography.bodyLarge

    TopAppBar(
        title = {
            Text(
                text = when (currentScreen) {
                    PdfViewerScreens.Start -> stringResource(R.string.app_name)
                    PdfViewerScreens.PdfViewer -> pdfUiState.documentName.ifEmpty {
                        stringResource(currentScreen.title)
                    }
                    else -> stringResource(currentScreen.title)
                },
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        },
        modifier = modifier,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_button)
                    )
                }
            }
        },
        actions = {
            if (currentScreen == PdfViewerScreens.PdfViewer) {
                IconButton(
                    onClick = onPreviousPageButtonClicked,
                    content = {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.action_previous)
                        )
                    },
                    enabled = pdfUiState.page > 1
                )
                IconButton(
                    onClick = onNextPageButtonClicked,
                    content = {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.action_next)
                        )
                    },
                    enabled = pdfUiState.page < pdfUiState.numPages,
                )
                IconButton(
                    onClick = onDropDownMenuButtonClicked,
                    content = {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.options_dropdown_menu)
                        )
                    }
                )
                DropdownMenu(
                    expanded = dropDownMenuShown,
                    onDismissRequest = { onDropDownMenuDismissRequest() },
                    modifier = Modifier.width(225.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_open),
                                style = dropDownMenuItemTextStyle
                            )
                        },
                        onClick = { onOpenDocumentDropdownMenuItemClicked() },
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.FileOpen, contentDescription = null)
                        },
                        enabled = true,
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_first),
                                style = dropDownMenuItemTextStyle
                            )
                        },
                        onClick = { onFirstPageDropdownMenuItemClicked() },
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.FirstPage, contentDescription = null)
                        },
                        enabled = true,
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_last),
                                style = dropDownMenuItemTextStyle
                            )
                        },
                        onClick = { onLastPageDropdownMenuItemClicked() },
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.LastPage, contentDescription = null)
                        },
                        enabled = true,
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_jump_to_page),
                                style = dropDownMenuItemTextStyle
                            )
                        },
                        onClick = { onJumpToPageDropdownMenuItemClicked() },
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Pageview, contentDescription = null)
                        },
                        enabled = true,
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_rotate_clockwise),
                                style = dropDownMenuItemTextStyle
                            )
                        },
                        onClick = { onRotateClockwiseDropdownMenuItemClicked() },
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Rotate90DegreesCw, contentDescription = null)
                        },
                        enabled = true,
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_rotate_counterclockwise),
                                style = dropDownMenuItemTextStyle
                            )
                        },
                        onClick = { onRotateCounterclockwiseDropdownMenuItemClicked() },
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Rotate90DegreesCcw, contentDescription = null)
                        },
                        enabled = true,
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_share),
                                style = dropDownMenuItemTextStyle
                            )
                        },
                        onClick = { onShareDropdownMenuItemClicked() },
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Share, contentDescription = null)
                        },
                        enabled = true,
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_save_as),
                                style = dropDownMenuItemTextStyle
                            )
                        },
                        onClick = { onSaveAsDropdownMenuItemClicked() },
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.SaveAs, contentDescription = null)
                        },
                        enabled = true,
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_view_document_properties),
                                style = dropDownMenuItemTextStyle
                            )
                        },
                        onClick = { onViewDocumentPropertiesDropdownMenuItemClicked() },
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Info, contentDescription = null)
                        },
                        enabled = true
                    )
                    if (BuildConfig.DEBUG) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.debug_action_toggle_text_layer_visibility),
                                    style = dropDownMenuItemTextStyle
                                )
                            },
                            onClick = { onToggleTextLayerVisibilityDropdownMenuItemClicked() },
                            leadingIcon = {
                                Icon(imageVector = Icons.Filled.Build, contentDescription = null)
                            },
                            enabled = true
                        )
                    }
                }
            }

            if (jumpToPageDialogShown) {
                AlertDialog(
                    onDismissRequest = onJumpToPageDialogDismissRequest,
                    confirmButton = jumpToPageDialogConfirmButton,
                    dismissButton = jumpToPageDialogDismissButton,
                    title = {
                        Text(
                            text = stringResource(R.string.action_jump_to_page),
                            style = typography.headlineSmall,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    },
                    text = {
                        jumpToPageDialogContent()
                    }
                )
            }

            if (propertiesDialogShown) {
                AlertDialog(
                    onDismissRequest = onPropertiesDialogDismissRequest,
                    confirmButton = propertiesDialogConfirmButton,
                    title = {
                        Text(
                            text = stringResource(R.string.action_view_document_properties),
                            style = typography.headlineSmall,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    },
                    text = {
                        propertiesDialogContent()
                    }
                )
            }

            if (passwordPromptDialogShown) {
                AlertDialog(
                    onDismissRequest = onPasswordPromptDialogDismissRequest,
                    confirmButton = passwordPromptDialogConfirmButton,
                    dismissButton = passwordPromptDialogDismissButton,
                    title = {
                        Text(
                            text = stringResource(R.string.password_prompt_description),
                            style = typography.headlineSmall,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    },
                    text = {
                        passwordPromptDialogContent()
                    }
                )
            }

            if (updateWebViewDialogShown) {
                AlertDialog(
                    onDismissRequest = {  },
                    confirmButton = {  },
                    title = {
                        Text(
                            text = stringResource(R.string.webview_out_of_date_title),
                            style = typography.headlineSmall,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    },
                    text = {
                        updateWebViewDialogContent()
                    }
                )
            }
        },
        windowInsets = windowInsets,
    )
}

@Composable
fun DocumentPropertyDialogItem(key: String, value: String) {
    Text(
        text = "$key:\n$value",
        style = typography.titleMedium
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerApp(
    modifier: Modifier,
    isActionView: Boolean,
) {
    val navController = rememberNavController()

    val backStackEntry by navController.currentBackStackEntryAsState()

    val currentScreen = PdfViewerScreens.valueOf(
        backStackEntry?.destination?.route ?: PdfViewerScreens.Start.name
    )

    val context = LocalContext.current

    val pdfViewModel: PdfViewModel = viewModel()

    val pdfUiState by pdfViewModel.uiState.collectAsState()

    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }

    val openPdfFileLauncher = rememberLauncherForActivityResult(contract = OpenDocument()) {
        if (it != null) {
            if (currentScreen != PdfViewerScreens.PdfViewer) {
                pdfViewModel.clearUiState()
                pdfViewModel.setUri(it)
                pdfViewModel.setPage(1)
                pdfViewModel.setEncryptedDocumentPassword("")
                navController.navigate(PdfViewerScreens.PdfViewer.name)
            } else {
                pdfViewModel.setUri(it)
                pdfViewModel.setPage(1)
                pdfViewModel.clearDocumentProperties()
                pdfViewModel.setEncryptedDocumentPassword("")
                pdfViewModel.loadPdf(context, snackbarHostState)
            }
        }
    }

    val errorWhileSavingString = stringResource(R.string.error_while_saving)

    val saveAsPdfFileLauncher = rememberLauncherForActivityResult(contract = CreateDocument(mimeTypePdf)) {
        if (it != null) {
            try {
                saveAs(context = context, existingUri = pdfUiState.uri, saveAs = it)
            } catch (e: IOException) {
                scope.launch {
                    snackbarHostState.showSnackbar(errorWhileSavingString)
                }
            } catch (e: OutOfMemoryError) {
                scope.launch {
                    snackbarHostState.showSnackbar(errorWhileSavingString)
                }
            } catch (e: IllegalArgumentException) {
                scope.launch {
                    snackbarHostState.showSnackbar(errorWhileSavingString)
                }
            }
        }
    }

    var jumpToPageDialogShown by rememberSaveable { mutableStateOf(false) }

    var jumpToPageDialogSelectedPage by rememberSaveable { mutableIntStateOf(pdfUiState.page) }

    var propertiesDialogShown by rememberSaveable { mutableStateOf(false) }

    var dropDownMenuShown by rememberSaveable { mutableStateOf(false) }

    var passwordPromptDialogShown by rememberSaveable { mutableStateOf(false) }

    var showInvalidPasswordError by rememberSaveable { mutableStateOf(false) }

    var isPasswordPromptDialogTextRedacted by rememberSaveable { mutableStateOf(true) }

    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    var updateWebViewDialogShown by rememberSaveable { mutableStateOf(false) }

    if (pdfUiState.numPages > 0) {
        LaunchedEffect(pdfUiState.page) {
            snackbarHostState.showSnackbar(
                message = "${pdfUiState.page}/${pdfUiState.numPages}",
                duration = SnackbarDuration.Short,
            )
        }
    }

    LaunchedEffect(Unit) {
        updateWebViewDialogShown = pdfViewModel.getWebViewRelease()!! < MIN_WEBVIEW_RELEASE
    }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                PdfViewerAppBar(
                    currentScreen = currentScreen,
                    canNavigateBack = navController.previousBackStackEntry != null,
                    navigateUp = { navController.navigateUp() },

                    pdfUiState = pdfUiState,

                    onPreviousPageButtonClicked = {
                        pdfViewModel.jumpToPageInDocument(
                            selectedPage = pdfUiState.page.dec(),
                        )
                    },
                    onNextPageButtonClicked = {
                        pdfViewModel.jumpToPageInDocument(
                            selectedPage = pdfUiState.page.inc(),
                        )
                    },

                    dropDownMenuShown = dropDownMenuShown,
                    onDropDownMenuButtonClicked = { dropDownMenuShown = !dropDownMenuShown },
                    onDropDownMenuDismissRequest = { dropDownMenuShown = false },

                    onOpenDocumentDropdownMenuItemClicked = {
                        dropDownMenuShown = false
                        openPdfFileLauncher.launch(
                            arrayOf(mimeTypePdf)
                        )
                    },

                    onFirstPageDropdownMenuItemClicked = {
                        pdfViewModel.jumpToPageInDocument(1)
                        dropDownMenuShown = false
                    },

                    onLastPageDropdownMenuItemClicked = {
                        pdfViewModel.jumpToPageInDocument(pdfUiState.numPages)
                        dropDownMenuShown = false
                    },

                    jumpToPageDialogShown = jumpToPageDialogShown,
                    onJumpToPageDialogDismissRequest = {
                        jumpToPageDialogShown = false
                        dropDownMenuShown = false
                    },
                    onJumpToPageDropdownMenuItemClicked = {
                        jumpToPageDialogShown = !jumpToPageDialogShown
                        dropDownMenuShown = false
                    },
                    jumpToPageDialogContent = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            AndroidView(
                                factory = { context ->
                                    NumberPicker(context).apply {
                                        clipToOutline = true
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.WRAP_CONTENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT
                                        )
                                        setOnValueChangedListener { picker, oldVal, newVal ->
                                            jumpToPageDialogSelectedPage = newVal
                                        }
                                        minValue = 1
                                        maxValue = pdfUiState.numPages
                                        value = pdfUiState.page
                                    }
                                },
                                update = {
                                    it.value = pdfUiState.page
                                },
                            )
                        }

                    },
                    jumpToPageDialogDismissButton = {
                        TextButton(
                            onClick = {
                                jumpToPageDialogShown = false
                                dropDownMenuShown = false
                            },
                            content = {
                                Text(
                                    text = stringResource(R.string.cancel)
                                )
                            }
                        )
                    },
                    jumpToPageDialogConfirmButton = {
                        TextButton(
                            onClick = {
                                pdfViewModel.jumpToPageInDocument(jumpToPageDialogSelectedPage)
                                jumpToPageDialogShown = false
                                dropDownMenuShown = false
                            },
                            content = {
                                Text(
                                    text = stringResource(android.R.string.ok)
                                )
                            }
                        )
                    },

                    onRotateClockwiseDropdownMenuItemClicked = {
                        pdfViewModel.documentOrientationChanged(+90)
                        dropDownMenuShown = false
                    },

                    onRotateCounterclockwiseDropdownMenuItemClicked = {
                        pdfViewModel.documentOrientationChanged(-90)
                        dropDownMenuShown = false
                    },

                    onShareDropdownMenuItemClicked = {
                        dropDownMenuShown = false

                        var sendIntent = Intent()

                        sendIntent = sendIntent.apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, pdfUiState.uri)
                            type = mimeTypePdf
                        }

                        val shareIntent = Intent.createChooser(sendIntent, null)
                        startActivity(context, shareIntent, ActivityOptionsCompat.makeBasic().toBundle())
                    },

                    onSaveAsDropdownMenuItemClicked = {
                        saveAsPdfFileLauncher.launch(pdfUiState.documentProperties.getValue(DocumentProperty.FileName))
                        dropDownMenuShown = false
                    },

                    onToggleTextLayerVisibilityDropdownMenuItemClicked = {
                        pdfUiState.webView.value?.evaluateJavascript("toggleTextLayerVisibility()", null)
                        dropDownMenuShown = false
                    },

                    propertiesDialogShown = propertiesDialogShown,
                    onPropertiesDialogDismissRequest = { propertiesDialogShown = false },
                    onViewDocumentPropertiesDropdownMenuItemClicked = {
                        propertiesDialogShown = !propertiesDialogShown
                        dropDownMenuShown = false
                    },
                    propertiesDialogContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            for (documentProperty in DocumentProperty.entries) {
                                val map = pdfUiState.documentProperties.filter { it.key == documentProperty }
                                map.forEach {
                                    DocumentPropertyDialogItem(stringResource(it.key.nameResource), it.value)
                                }
                            }
                        }
                    },
                    propertiesDialogConfirmButton = {
                        TextButton(
                            onClick = {
                                propertiesDialogShown = false
                            },
                            enabled = pdfUiState.encryptedDocumentPassword.isNotEmpty(),
                            content = {
                                Text(
                                    text = stringResource(android.R.string.ok)
                                )
                            },
                        )
                    },

                    passwordPromptDialogShown = passwordPromptDialogShown,
                    onPasswordPromptDialogDismissRequest = {  },
                    passwordPromptDialogContent = {
                        TextField(
                            value = pdfUiState.encryptedDocumentPassword,
                            onValueChange = {
                                pdfViewModel.setEncryptedDocumentPassword(it)
                                showInvalidPasswordError = false
                            },
                            placeholder = {
                                Text(stringResource(R.string.password_prompt_hint))
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { 
                                        isPasswordPromptDialogTextRedacted = !isPasswordPromptDialogTextRedacted
                                    },
                                    enabled = !showInvalidPasswordError,
                                ) {
                                    Icon(
                                        imageVector = if (showInvalidPasswordError) {
                                            Icons.Filled.ErrorOutline
                                        } else if (isPasswordPromptDialogTextRedacted) {
                                                Icons.Filled.Visibility
                                            } else {
                                                Icons.Filled.VisibilityOff
                                        },
                                        contentDescription = stringResource(R.string.password_prompt_hint)
                                    )
                                }               
                            },
                            isError = showInvalidPasswordError,
                            visualTransformation = if (isPasswordPromptDialogTextRedacted) {
                                VisualTransformation {
                                    TransformedText(
                                        AnnotatedString("*".repeat(pdfUiState.encryptedDocumentPassword.length)),
                                        OffsetMapping.Identity
                                    )
                                }
                            } else {
                                VisualTransformation.None
                            },
                            supportingText = {
                                if (showInvalidPasswordError) {
                                    Text(stringResource(R.string.password_invalid_password))
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrect = false,
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { pdfViewModel.loadPdfWithPassword() }
                            ),
                            singleLine = true,
                        )
                    },
                    passwordPromptDialogDismissButton = {
                        TextButton(
                            onClick = {
                                passwordPromptDialogShown = false
                                navController.navigateUp()
                            },
                            content = {
                                Text(
                                    text = stringResource(R.string.cancel)
                                )
                            }
                        )
                    },
                    passwordPromptDialogConfirmButton = {
                        TextButton(
                            onClick = {
                                pdfViewModel.loadPdfWithPassword()
                            },
                            enabled = pdfUiState.encryptedDocumentPassword.isNotEmpty(),
                            content = {
                                Text(
                                    text = stringResource(R.string.open)
                                )
                            }
                        )                                    
                    },

                    updateWebViewDialogShown = updateWebViewDialogShown,
                    updateWebViewDialogContent = {
                        Text(
                            stringResource(
                                id = R.string.webview_out_of_date_message,
                                pdfViewModel.getWebViewRelease()!!,
                                MIN_WEBVIEW_RELEASE
                            )
                        )
                    },

                    windowInsets = if (isFullscreen) {
                        WindowInsets(0, 0, 0, 0)
                    } else {
                        TopAppBarDefaults.windowInsets
                    },

                    modifier = modifier
                )
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp, 0.dp),
                        horizontalArrangement = Arrangement.Absolute.Right
                    ) {
                        Text(
                            modifier = Modifier
                                .background(colorScheme.background)
                                .padding(2.dp, 0.dp),
                            text = it.visuals.message,
                            style = typography.titleMedium.copy(fontSize = TextUnit(18F, TextUnitType.Sp))
                        )
                    }
                }
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            // Use this instead of deeplinks so that when back is pressed
            // it goes to the app this app was opened by instead of the home screen.
            // Deeplinks not doing that is intentional, see figure 4 at
            // https://developer.android.com/guide/navigation/principles#deep-link
            startDestination = if (isActionView) {
                PdfViewerScreens.PdfViewer.name
            } else {
                PdfViewerScreens.Start.name
            },
            modifier = if (isFullscreen) {
                modifier
            } else {
                modifier.padding(
                    innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                    innerPadding.calculateTopPadding(),
                    innerPadding.calculateEndPadding(LayoutDirection.Ltr)
                )
            },
        ) {
            composable(route = PdfViewerScreens.Start.name) {
                StartupScreen(
                    modifier = modifier,
                    onOpenPdfButtonClicked = {
                        openPdfFileLauncher.launch(
                            arrayOf(mimeTypePdf),
                            ActivityOptionsCompat.makeBasic(),
                        )
                    },
                    onLaunchedEffect = {
                        pdfViewModel.clearUiState()
                        if (isFullscreen) {
                            isFullscreen = false
                        }
                    }
                )
            }
            composable(
                route = PdfViewerScreens.PdfViewer.name,
            ) {
                PdfViewerScreen(
                    pdfViewModel = pdfViewModel,
                    pdfUiState = pdfUiState,
                    snackbarHostState = snackbarHostState,
                    navigateUp = { navController.navigateUp() },
                    onGestureHelperTapUp = { 
                        pdfUiState.webView.value?.evaluateJavascript("isTextSelected()") { selection ->
                            if (!selection.toBoolean()) {
                                isFullscreen = !isFullscreen
                            }
                        }
                    },
                    onShowPasswordPrompt = {
                        passwordPromptDialogShown = true
                    },
                    onInvalidPassword = {
                        showInvalidPasswordError = true
                        pdfViewModel.setEncryptedDocumentPassword("")
                    },
                    onOnLoaded = {
                        passwordPromptDialogShown = false
                    }
                )
            }
        }
    }
}
