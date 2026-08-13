package com.otakeeesen.byebyemoneylist.ui.components.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.WidgetData
import com.otakeeesen.byebyemoneylist.ui.viewmodel.DashboardViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAddWidgetDialog by remember { mutableStateOf(false) }

    var localWidgets by remember(uiState.widgets) { mutableStateOf(uiState.widgets) }
    var isAnyDragging by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.widgets, isAnyDragging) {
        if (!isAnyDragging) {
            localWidgets = uiState.widgets
        }
    }

    val lazyGridState = rememberLazyGridState()
    val reorderableGridState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
        localWidgets = localWidgets.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { showAddWidgetDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_widget)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.widgets.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dashboard,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.empty_dashboard_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.empty_dashboard_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showAddWidgetDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.add_widget))
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = localWidgets,
                        key = { it.config.id },
                        span = { GridItemSpan(1) }
                    ) { widget ->
                        val data = uiState.widgetDataMap[widget.config.id] ?: WidgetData.Loading
                        ReorderableItem(
                            state = reorderableGridState,
                            key = widget.config.id
                        ) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                            widget.Card(
                                data = data,
                                onTap = widget.createOnTap(navController, context),
                                onLongPress = { viewModel.requestRemoveWidget(widget.config) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { shadowElevation = elevation.value },
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStarted = { isAnyDragging = true },
                                    onDragStopped = {
                                        isAnyDragging = false
                                        viewModel.reorderWidgets(localWidgets)
                                    }
                                )
                            )
                        }
                    }
                }
            }

            // Dialogs
            if (showAddWidgetDialog) {
                AddWidgetDialog(
                    categories = uiState.categories,
                    onDismiss = { showAddWidgetDialog = false },
                    onConfirm = { type, categoryId ->
                        viewModel.addWidget(type, categoryId)
                        showAddWidgetDialog = false
                    }
                )
            }

            if (uiState.widgetToRemove != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.cancelRemoveWidget() },
                    title = { Text(text = stringResource(R.string.remove_widget_title)) },
                    text = { Text(text = stringResource(R.string.remove_widget_confirm)) },
                    confirmButton = {
                        TextButton(
                            onClick = { viewModel.confirmRemoveWidget() }
                        ) {
                            Text(text = stringResource(R.string.delete))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { viewModel.cancelRemoveWidget() }
                        ) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    }
                )
            }
        }
    }
}
