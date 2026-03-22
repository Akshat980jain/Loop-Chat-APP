package com.loopchat.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.loopchat.app.ui.theme.Background
import com.loopchat.app.ui.theme.Primary
import com.loopchat.app.ui.theme.SurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollComposerBottomSheet(
    onDismiss: () -> Unit,
    onCreatePoll: (question: String, options: List<String>, isMultipleChoice: Boolean, isAnonymous: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") } // Start with 2 empty options
    var isMultipleChoice by remember { mutableStateOf(false) }
    var isAnonymous by remember { mutableStateOf(false) }

    val canAddOption = options.size < 6
    val isValid = question.isNotBlank() && options.filter { it.isNotBlank() }.size >= 2

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Background,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Create a Poll",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Question Input
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("Ask a question") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceVariant
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            
            // Dynamic Options List
            Column(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, optionText ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = optionText,
                            onValueChange = { options[index] = it },
                            placeholder = { Text("Option \${index + 1}") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                        
                        if (options.size > 2) {
                            IconButton(onClick = { options.removeAt(index) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove Option", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                
                if (canAddOption) {
                    TextButton(
                        onClick = { options.add("") },
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add Option (\${6 - options.size} left)")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Settings Toggles
            Surface(
                color = SurfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Multiple Answers", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Allow users to vote for more than one option", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isMultipleChoice,
                            onCheckedChange = { isMultipleChoice = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = Primary.copy(alpha = 0.5f))
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Anonymous Voting", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Hide who voted for what", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isAnonymous,
                            onCheckedChange = { isAnonymous = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = Primary.copy(alpha = 0.5f))
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { 
                    val validOptions = options.filter { it.isNotBlank() }
                    onCreatePoll(question, validOptions, isMultipleChoice, isAnonymous) 
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Create Poll", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
