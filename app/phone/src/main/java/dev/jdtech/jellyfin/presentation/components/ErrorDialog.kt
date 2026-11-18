package dev.jdtech.jellyfin.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

private data class ErrorText(@StringRes val title: Int, @StringRes val message: Int)

private fun errorTextFor(exception: Throwable): ErrorText {
    val chain = generateSequence(exception) { it.cause }.toList()
    val names = chain.joinToString(" ") { it.javaClass.simpleName }.lowercase()
    val messages = chain.mapNotNull { it.message }.joinToString(" ").lowercase()
    val all = "$names $messages"

    return when {
        listOf("unknownhost", "connectexception", "sockettimeout", "timeout", "ssl").any {
            all.contains(it)
        } || all.contains("connection failed") || all.contains("unable to resolve host") ->
            ErrorText(CoreR.string.error_connection_title, CoreR.string.error_connection_message)
        all.contains("401") ||
            all.contains("403") ||
            all.contains("unauthorized") ||
            all.contains("forbidden") ||
            all.contains("authentication") ->
            ErrorText(CoreR.string.error_auth_title, CoreR.string.error_auth_message)
        all.contains("404") || all.contains("not found") ->
            ErrorText(CoreR.string.error_not_found_title, CoreR.string.error_not_found_message)
        Regex("(^|\\D)5\\d\\d($|\\D)").containsMatchIn(messages) ||
            all.contains("server error") ||
            all.contains("internal server") ->
            ErrorText(CoreR.string.error_server_title, CoreR.string.error_server_message)
        all.contains("json") ||
            all.contains("serial") ||
            all.contains("parse") ||
            all.contains("unexpected response") ->
            ErrorText(CoreR.string.error_parse_title, CoreR.string.error_parse_message)
        else -> ErrorText(CoreR.string.error_loading_data, CoreR.string.error_unknown_message)
    }
}

@Composable
fun ErrorDialog(exception: Throwable, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    var showDetails by rememberSaveable { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        if (showDetails) {
            ErrorDetailsDialog(
                exception = exception,
                onBack = { showDetails = false },
                onDismissRequest = onDismissRequest,
            )
        } else {
            val errorText = errorTextFor(exception)
            ErrorSummaryDialog(
                errorText = errorText,
                onShowDetails = { showDetails = true },
                onCopy = { copyError(context, exception) },
                onDismissRequest = onDismissRequest,
            )
        }
    }
}

@Composable
private fun ErrorSummaryDialog(
    errorText: ErrorText,
    onShowDetails: () -> Unit,
    onCopy: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column {
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
            Text(
                text = stringResource(errorText.title),
                modifier =
                    Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacings.default),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
            Text(
                text = stringResource(errorText.message),
                modifier =
                    Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacings.default),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
            HorizontalDivider()
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(
                            start = MaterialTheme.spacings.medium,
                            top = MaterialTheme.spacings.extraSmall,
                            end = MaterialTheme.spacings.medium,
                            bottom = MaterialTheme.spacings.small,
                        ),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onShowDetails) {
                    Text(stringResource(CoreR.string.error_details))
                }
                TextButton(onClick = onCopy) { Text(stringResource(CoreR.string.copy)) }
                TextButton(onClick = onDismissRequest) { Text(stringResource(CoreR.string.close)) }
            }
        }
    }
}

@Composable
private fun ErrorDetailsDialog(
    exception: Throwable,
    onBack: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth().heightIn(200.dp, max = 540.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column {
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
            Text(
                text = stringResource(CoreR.string.error_details),
                modifier =
                    Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacings.default),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
            HorizontalDivider()
            Column(
                modifier =
                    Modifier.weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
                Text(
                    text = exception.stackTraceToString(),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider()
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(
                            start = MaterialTheme.spacings.medium,
                            top = MaterialTheme.spacings.extraSmall,
                            end = MaterialTheme.spacings.medium,
                            bottom = MaterialTheme.spacings.small,
                        ),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onBack) { Text(stringResource(CoreR.string.back)) }
                TextButton(onClick = { copyError(context, exception) }) {
                    Text(stringResource(CoreR.string.copy))
                }
                TextButton(onClick = { shareError(context, exception) }) {
                    Text(stringResource(CoreR.string.share))
                }
                TextButton(onClick = onDismissRequest) { Text(stringResource(CoreR.string.close)) }
            }
        }
    }
}

private fun copyError(context: Context, exception: Throwable) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("PerthFin error", errorText(exception)))
    Toast.makeText(context, context.getString(CoreR.string.copied), Toast.LENGTH_SHORT).show()
}

private fun shareError(context: Context, exception: Throwable) {
    val sendIntent =
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, errorText(exception))
            type = "text/plain"
        }
    context.startActivity(Intent.createChooser(sendIntent, null))
}

private fun errorText(exception: Throwable): String = buildString {
    append(exception.message ?: "")
    append("\n\n")
    append(exception.stackTraceToString())
}

@Preview
@Composable
private fun ErrorDialogPreview() {
    FindroidTheme {
        ErrorDialog(exception = Exception("Error loading data"), onDismissRequest = {})
    }
}
