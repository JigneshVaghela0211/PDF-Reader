package com.pdf.pdfreader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdf.pdfreader.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchTopBar(
    query: String,
    matchCount: Int,
    currentMatch: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search pill field
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_pdf_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            // Match counter
            if (query.isNotEmpty()) {
                Text(
                    text = if (matchCount > 0 && currentMatch >= 0)
                        "${currentMatch + 1} / $matchCount"
                    else "0",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (matchCount == 0 && query.isNotEmpty())
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Prev
            IconButton(
                onClick = onPrevious,
                enabled = matchCount > 0,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous", modifier = Modifier.size(22.dp))
            }

            // Next
            IconButton(
                onClick = onNext,
                enabled = matchCount > 0,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next", modifier = Modifier.size(22.dp))
            }

            // Close
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_search), modifier = Modifier.size(22.dp))
            }
        }
    }
}
