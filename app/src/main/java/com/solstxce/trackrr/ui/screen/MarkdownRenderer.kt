package com.solstxce.trackrr.ui.screen

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import io.noties.markwon.Markwon
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin

@Composable
fun MarkdownContent(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
                setLineSpacing(0f, 1.2f)
                textSize = 15.sp.value
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            val source = if (markdown.isBlank()) "No release description provided." else markdown
            markwon.setMarkdown(textView, source)
        }
    )
}
