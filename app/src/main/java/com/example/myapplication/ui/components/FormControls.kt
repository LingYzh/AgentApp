package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** V3 form field: persistent label above a filled input, with help below. */
@Composable
fun PrototypeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        label?.let {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) { it() }
            }
        }
        OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(), enabled = enabled,
            readOnly = readOnly, textStyle = textStyle, placeholder = placeholder,
            leadingIcon = leadingIcon, trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
            isError = isError, visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            singleLine = singleLine, maxLines = maxLines, minLines = minLines,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ))
        supportingText?.let {
            CompositionLocalProvider(LocalContentColor provides if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) {
                ProvideTextStyle(MaterialTheme.typography.bodySmall) { it() }
            }
        }
    }
}

@Composable
fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall)
        description?.let { Text(it, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        content()
    }
}
