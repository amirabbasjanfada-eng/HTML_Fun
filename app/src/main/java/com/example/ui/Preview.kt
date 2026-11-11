package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import com.example.web.ContentServer
import com.example.web.PaperWebViewClient
import com.example.web.WebViewSetup

/**
 * The in-app preview. It is a normal hardware-accelerated WebView (so WebGL works here), created
 * once and then only navigated when [url] changes. It is rebuilt only if its renderer process dies.
 */
@Composable
fun WebPreview(
    url: String,
    server: ContentServer,
    touchEnabled: Boolean,
    onConsole: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var generation by remember { mutableIntStateOf(0) }

    Box(modifier = modifier) {
        key(generation) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebViewSetup.create(
                        context,
                        PaperWebViewClient(server, renderGone = { generation += 1 }),
                        onConsole
                    )
                },
                update = { view ->
                    if (view.tag != url) {
                        view.tag = url
                        view.loadUrl(url)
                    }
                },
                onRelease = { view -> WebViewSetup.dispose(view) }
            )
        }
        if (!touchEnabled) {
            // Swallows touches meant for the page, so the surrounding page can still be scrolled.
            Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {})
        }
    }
}
