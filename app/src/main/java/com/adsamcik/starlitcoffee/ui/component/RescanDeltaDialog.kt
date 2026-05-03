@file:Suppress(
    // The matching declaration `RescanDeltaDialog` exists in this file, but
    // is preceded by the `FieldDelta` data class + helper functions that
    // power it. Reordering would split tightly-coupled types from their
    // dialog without improving readability.
    "MatchingDeclarationName",
)

package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.util.DateParser
import com.adsamcik.starlitcoffee.util.WeightParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FieldDelta(
    val label: String,
    val oldValue: String,
    val newValue: String,
)

/**
 * Compare resolved scan fields against an existing bag and return changed fields.
 */
@Suppress(
    // 14 fields × per-field guards. Branch count = field count.
    "CyclomaticComplexMethod",
)
fun buildFieldDeltas(
    bag: CoffeeBagEntity,
    resolvedFields: Map<String, String>,
): List<FieldDelta> {
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val deltas = mutableListOf<FieldDelta>()

    fun check(label: String, fieldKey: String, currentValue: String?) {
        val scanned = resolvedFields[fieldKey]?.trim()?.takeIf { it.isNotBlank() } ?: return
        val current = currentValue?.trim()?.takeIf { it.isNotBlank() }
        if (current == null || !current.equals(scanned, ignoreCase = true)) {
            deltas.add(FieldDelta(label, current ?: "—", scanned))
        }
    }

    check("Name", "name", bag.name)
    check("Roaster", "roaster", bag.roaster)
    check("Origin", "origin", bag.origin)
    check("Region", "region", bag.region)
    check("Variety", "variety", bag.variety)
    check("Roast level", "roastLevel", bag.roastLevel)
    check("Process", "processType", bag.processType)
    check("Tasting notes", "tastingNotes", bag.tastingNotes)
    check("Farm", "farm", bag.farm)
    check("Altitude", "altitude", bag.altitude)

    // Weight: compare parsed grams
    resolvedFields["weight"]?.let { scannedWeight ->
        val parsedG = WeightParser.parseToGrams(scannedWeight)
        if (parsedG != null) {
            val currentG = bag.weightG ?: bag.initialWeightG
            if (currentG == null || kotlin.math.abs(currentG - parsedG) > 0.5f) {
                deltas.add(FieldDelta(
                    "Weight",
                    currentG?.let { "${it.toInt()}g" } ?: "—",
                    "${parsedG.toInt()}g",
                ))
            }
        }
    }

    // Roast date: compare parsed millis
    resolvedFields["roastDate"]?.let { scannedDate ->
        val parsedMs = DateParser.parse(scannedDate)
        if (parsedMs != null) {
            val currentMs = bag.roastDate
            if (currentMs == null || kotlin.math.abs(currentMs - parsedMs) > 86_400_000L) {
                deltas.add(FieldDelta(
                    "Roast date",
                    currentMs?.let { dateFormat.format(Date(it)) } ?: "—",
                    dateFormat.format(Date(parsedMs)),
                ))
            }
        }
    }

    // Expiry date
    resolvedFields["expiryDate"]?.let { scannedDate ->
        val parsedMs = DateParser.parse(scannedDate)
        if (parsedMs != null) {
            val currentMs = bag.expiryDate
            if (currentMs == null || kotlin.math.abs(currentMs - parsedMs) > 86_400_000L) {
                deltas.add(FieldDelta(
                    "Expiry date",
                    currentMs?.let { dateFormat.format(Date(it)) } ?: "—",
                    dateFormat.format(Date(parsedMs)),
                ))
            }
        }
    }

    return deltas
}

/**
 * Apply resolved scan fields onto an existing bag entity, returning the updated copy.
 */
fun applyDeltaToBag(
    bag: CoffeeBagEntity,
    resolvedFields: Map<String, String>,
): CoffeeBagEntity {
    var updated = bag
    resolvedFields["name"]?.trim()?.takeIf { it.isNotBlank() }?.let {
        updated = updated.copy(name = it)
    }
    resolvedFields["roaster"]?.trim()?.takeIf { it.isNotBlank() }?.let {
        updated = updated.copy(roaster = it)
    }
    resolvedFields["origin"]?.trim()?.takeIf { it.isNotBlank() }?.let {
        updated = updated.copy(origin = it)
    }
    resolvedFields["region"]?.trim()?.takeIf { it.isNotBlank() }?.let {
        updated = updated.copy(region = it)
    }
    resolvedFields["variety"]?.trim()?.takeIf { it.isNotBlank() }?.let {
        updated = updated.copy(variety = it)
    }
    resolvedFields["roastLevel"]?.trim()?.takeIf { it.isNotBlank() }?.let {
        updated = updated.copy(roastLevel = it)
    }
    resolvedFields["processType"]?.trim()?.takeIf { it.isNotBlank() }?.let {
        updated = updated.copy(processType = it)
    }
    resolvedFields["tastingNotes"]?.trim()?.takeIf { it.isNotBlank() }?.let {
        updated = updated.copy(tastingNotes = it)
    }
    resolvedFields["farm"]?.trim()?.takeIf { it.isNotBlank() }?.let {
        updated = updated.copy(farm = it)
    }
    resolvedFields["altitude"]?.trim()?.takeIf { it.isNotBlank() }?.let {
        updated = updated.copy(altitude = it)
    }
    resolvedFields["weight"]?.let { w ->
        WeightParser.parseToGrams(w)?.let { g ->
            updated = updated.copy(weightG = g, initialWeightG = g)
        }
    }
    resolvedFields["roastDate"]?.let { d ->
        DateParser.parse(d)?.let { ms -> updated = updated.copy(roastDate = ms) }
    }
    resolvedFields["expiryDate"]?.let { d ->
        DateParser.parse(d)?.let { ms -> updated = updated.copy(expiryDate = ms) }
    }
    return updated
}

@Composable
fun RescanDeltaDialog(
    bag: CoffeeBagEntity,
    resolvedFields: Map<String, String>,
    onUpdateBag: (CoffeeBagEntity) -> Unit,
    onNewBag: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val deltas = buildFieldDeltas(bag, resolvedFields)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (deltas.isEmpty()) "No changes found" else "Rescan results",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            if (deltas.isEmpty()) {
                Text(
                    text = stringResource(R.string.msg_rescan_no_changes),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column {
                    Text(
                        text = "${deltas.size} field${if (deltas.size != 1) "s" else ""} changed:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(deltas) { delta ->
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                ) {
                                    Text(
                                        text = delta.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = delta.oldValue,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = "→",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                        )
                                        Text(
                                            text = delta.newValue,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (deltas.isNotEmpty()) {
                TextButton(onClick = {
                    onUpdateBag(applyDeltaToBag(bag, resolvedFields))
                }) {
                    Text(stringResource(R.string.action_update_bag))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        },
        dismissButton = {
            if (deltas.isNotEmpty()) {
                Row {
                    TextButton(onClick = { onNewBag(resolvedFields) }) {
                        Text(stringResource(R.string.action_new_bag))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        },
    )
}
