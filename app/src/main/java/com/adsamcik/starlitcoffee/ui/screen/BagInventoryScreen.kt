package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.model.CoffeeBagStatus
import com.adsamcik.starlitcoffee.navigation.BarcodeScanner
import com.adsamcik.starlitcoffee.ui.component.DetailRow
import com.adsamcik.starlitcoffee.ui.component.EmptyStateBox
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BagInventoryScreen(
    navController: NavController,
    brewViewModel: BrewViewModel,
) {
    val bags by brewViewModel.coffeeBags.collectAsStateWithLifecycle()
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val scannedBarcode by (currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow<String?>("scanned_barcode", null)
        ?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) })

    var showAddSheet by remember { mutableStateOf(false) }
    var selectedBag by remember { mutableStateOf<CoffeeBagEntity?>(null) }
    var pendingBarcode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(scannedBarcode) {
        val barcode = scannedBarcode ?: return@LaunchedEffect
        currentBackStackEntry?.savedStateHandle?.set("scanned_barcode", null)
        brewViewModel.findBagByBarcode(barcode) { bag ->
            if (bag != null) {
                selectedBag = bag
                showAddSheet = false
                pendingBarcode = null
            } else {
                pendingBarcode = barcode
                showAddSheet = true
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    pendingBarcode = null
                    showAddSheet = true
                },
                shape = RoundedCornerShape(28.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Bag")
            }
        },
    ) { innerPadding ->
        if (bags.isEmpty()) {
            EmptyStateBox(
                icon = Icons.Filled.ShoppingBag,
                message = "No coffee bags yet",
                subtitle = "Track your beans — add roast details and tasting notes",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 16.dp,
                    bottom = 88.dp,
                ),
            ) {
                item {
                    Text(
                        text = "Coffee Bags",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .padding(start = 8.dp, bottom = 8.dp)
                            .semantics { heading() },
                    )
                }
                items(bags, key = { it.id }) { bag ->
                    BagCard(
                        bag = bag,
                        dateFormat = dateFormat,
                        onTap = { selectedBag = bag },
                    )
                }
            }
        }
    }

    // Add bag bottom sheet
    if (showAddSheet) {
        AddBagSheet(
            initialBarcode = pendingBarcode,
            onDismiss = {
                showAddSheet = false
                pendingBarcode = null
            },
            onScanBarcode = {
                showAddSheet = false
                navController.navigate(BarcodeScanner)
            },
            onSave = { name, roaster, origin, roastLevel, barcode, weightG, notes ->
                brewViewModel.addCoffeeBag(
                    name = name,
                    roaster = roaster,
                    origin = origin,
                    roastLevel = roastLevel,
                    barcode = barcode,
                    weightG = weightG,
                    notes = notes,
                )
                showAddSheet = false
                pendingBarcode = null
            },
        )
    }

    // Bag detail bottom sheet
    selectedBag?.let { bag ->
        BagDetailSheet(
            bag = bag,
            dateFormat = dateFormat,
            onDismiss = { selectedBag = null },
            onStatusChange = { status ->
                brewViewModel.updateBagStatus(bag.id, status.name)
                selectedBag = bag.copy(status = status.name)
            },
            onDelete = {
                brewViewModel.deleteCoffeeBag(bag)
                selectedBag = null
            },
        )
    }
}

@Composable
private fun BagCard(
    bag: CoffeeBagEntity,
    dateFormat: SimpleDateFormat,
    onTap: () -> Unit,
) {
    val statusColor = when (bag.status) {
        "SEALED" -> MaterialTheme.colorScheme.outline
        "OPEN" -> MaterialTheme.colorScheme.primary
        "FROZEN" -> MaterialTheme.colorScheme.tertiary
        "FINISHED" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.outline
    }

    ElevatedCard(
        onClick = onTap,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bag.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (bag.roaster != null) {
                    Text(
                        text = bag.roaster,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                bag.weightG?.let { w ->
                    Text(
                        text = "${"%.0f".format(w)}g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (bag.roastDate != null) {
                    Text(
                        text = "Roasted: ${dateFormat.format(Date(bag.roastDate))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        bag.status.lowercase()
                            .replaceFirstChar { it.uppercase() },
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = statusColor,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBagSheet(
    initialBarcode: String? = null,
    onDismiss: () -> Unit,
    onScanBarcode: () -> Unit,
    onSave: (
        name: String,
        roaster: String?,
        origin: String?,
        roastLevel: String?,
        barcode: String?,
        weightG: Float?,
        notes: String?,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var roaster by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf("") }
    var roastLevel by remember { mutableStateOf("") }
    var barcode by remember(initialBarcode) { mutableStateOf(initialBarcode.orEmpty()) }
    var weight by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = "Add Coffee Bag",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            if (initialBarcode != null) {
                OutlinedTextField(
                    value = initialBarcode,
                    onValueChange = {},
                    label = { Text("Barcode") },
                    shape = RoundedCornerShape(16.dp),
                    readOnly = true,
                    enabled = false,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name *") },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = roaster,
                onValueChange = { roaster = it },
                label = { Text("Roaster") },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = origin,
                onValueChange = { origin = it },
                label = { Text("Origin") },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = roastLevel,
                onValueChange = { roastLevel = it },
                label = { Text("Roast level") },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                label = { Text("Weight (g)") },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                shape = RoundedCornerShape(16.dp),
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            OutlinedButton(
                onClick = {
                    onDismiss()
                    onScanBarcode()
                },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 0.dp),
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Barcode")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            name,
                            roaster.takeIf { it.isNotBlank() },
                            origin.takeIf { it.isNotBlank() },
                            roastLevel.takeIf { it.isNotBlank() },
                            barcode.takeIf { it.isNotBlank() },
                            weight.toFloatOrNull(),
                            notes.takeIf { it.isNotBlank() },
                        )
                    }
                },
                shape = RoundedCornerShape(28.dp),
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BagDetailSheet(
    bag: CoffeeBagEntity,
    dateFormat: SimpleDateFormat,
    onDismiss: () -> Unit,
    onStatusChange: (CoffeeBagStatus) -> Unit,
    onDelete: () -> Unit,
) {
    var statusMenuExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = bag.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (bag.roaster != null) {
                Text(
                    text = "by ${bag.roaster}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (bag.origin != null) {
                DetailRow("Origin", bag.origin)
            }
            if (bag.roastLevel != null) {
                DetailRow("Roast", bag.roastLevel)
            }
            if (bag.roastDate != null) {
                DetailRow("Roast date", dateFormat.format(Date(bag.roastDate)))
            }
            if (bag.weightG != null) {
                DetailRow("Weight", "${"%.0f".format(bag.weightG)}g")
            }
            if (bag.notes != null) {
                DetailRow("Notes", bag.notes)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status changer
            Box {
                OutlinedButton(
                    onClick = { statusMenuExpanded = true },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text("Status: ${bag.status.lowercase().replaceFirstChar { it.uppercase() }}")
                }
                DropdownMenu(
                    expanded = statusMenuExpanded,
                    onDismissRequest = { statusMenuExpanded = false },
                ) {
                    CoffeeBagStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.displayName) },
                            onClick = {
                                onStatusChange(status)
                                statusMenuExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Delete Bag",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

