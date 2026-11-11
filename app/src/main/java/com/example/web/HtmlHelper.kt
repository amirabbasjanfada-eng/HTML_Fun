package com.example.web

/**
 * Prepares wallpaper HTML at *serve time*. Stored code stays exactly what the user wrote; the helper
 * block below is added on the fly and can always be removed again with [stripInjected].
 */
object HtmlHelper {

    private const val VIEWPORT_META =
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, " +
            "maximum-scale=1.0, user-scalable=no, viewport-fit=cover\">"

    private val regexOptions = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    // Only leading whitespace is consumed, so strip(prepare(x)) restores x exactly.
    private val injectedStyleRegex =
        Regex("\\s*<style[^>]*id=[\"']webpaper-injected-styles[\"'][^>]*>.*?</style>", regexOptions)
    private val injectedScriptRegex =
        Regex("\\s*<script[^>]*id=[\"']webpaper-injected-script[\"'][^>]*>.*?</script>", regexOptions)
    private val viewportRegex =
        Regex("<meta[^>]+name\\s*=\\s*[\"']viewport[\"']", RegexOption.IGNORE_CASE)
    private val headOpenRegex = Regex("<head(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
    private val htmlOpenRegex = Regex("<html(\\s[^>]*)?>", RegexOption.IGNORE_CASE)

    private val injectedCss = """
        <style id="webpaper-injected-styles">
            html, body {
                margin: 0 !important;
                padding: 0 !important;
                width: 100% !important;
                height: 100% !important;
                overflow: hidden !important;
                overscroll-behavior: none !important;
                user-select: none !important;
                -webkit-user-select: none !important;
                -webkit-touch-callout: none !important;
                touch-action: none !important;
                background-color: #000000;
            }
            canvas { display: block; touch-action: none !important; }
            * { -webkit-tap-highlight-color: transparent !important; }
            ::-webkit-scrollbar { display: none !important; width: 0 !important; height: 0 !important; }
        </style>
    """.trimIndent()

    private val coreScriptJs = """
        var W = window;
        W.__webpaperFps = __FPS__;
        W.__webpaperFrames = 0;

        // Dialogs would block a wallpaper forever.
        W.alert = function (m) { console.log('[WebPaper] alert suppressed: ' + m); };
        W.confirm = function () { return false; };
        W.prompt = function () { return null; };
        document.addEventListener('contextmenu', function (e) { e.preventDefault(); });

        // Frame-rate cap. Pages keep calling requestAnimationFrame as usual; the wrapper skips
        // vsyncs so the page (and the GPU) does less work. __webpaperFps = 0 means "no cap".
        var nativeRaf = W.requestAnimationFrame.bind(W);
        var nativeCaf = W.cancelAnimationFrame.bind(W);
        var pending = {};
        var nextId = 0;
        var lastFrame = -1;
        W.requestAnimationFrame = function (callback) {
            var id = ++nextId;
            var tick = function (ts) {
                var fps = W.__webpaperFps;
                if (fps > 0 && lastFrame >= 0 && ts !== lastFrame && ts - lastFrame < 1000 / fps - 2) {
                    pending[id] = nativeRaf(tick); // too early: wait for a later vsync
                    return;
                }
                lastFrame = ts;
                delete pending[id];
                callback(ts);
            };
            pending[id] = nativeRaf(tick);
            return id;
        };
        W.cancelAnimationFrame = function (id) {
            if (pending[id] !== undefined) {
                nativeCaf(pending[id]);
                delete pending[id];
            }
        };

        // Heartbeat: lets the wallpaper service verify that this page is really being rendered.
        var counted = 0;
        nativeRaf(function heartbeat() {
            W.__webpaperFrames = ++counted;
            if (counted < 5) nativeRaf(heartbeat);
        });

        // Pages that measure the window too early get a second chance.
        W.addEventListener('load', function () {
            setTimeout(function () {
                try { W.dispatchEvent(new Event('resize')); } catch (e) {}
            }, 100);
        });
    """.trimIndent()

    private val touchPolyfillJs = """
        // Touch-to-mouse bridge for pages that only listen to mouse events. Only the primary finger
        // is forwarded, a click is only produced for a real tap (not at the end of a drag), and the
        // browser's own emulated mouse events are cancelled so nothing fires twice.
        var TAP_SLOP = 12;
        var TAP_TIMEOUT = 600;
        var start = null;

        function forwardTouchToMouse(touch, type, target) {
            try {
                var mouseEvent = new MouseEvent(type, {
                    bubbles: true,
                    cancelable: true,
                    view: window,
                    button: 0,
                    buttons: type === 'mouseup' || type === 'click' ? 0 : 1,
                    screenX: touch.screenX,
                    screenY: touch.screenY,
                    clientX: touch.clientX,
                    clientY: touch.clientY
                });
                (target || touch.target || document).dispatchEvent(mouseEvent);
            } catch (e) {}
        }

        document.addEventListener('touchstart', function (e) {
            if (e.touches && e.touches.length === 1) {
                var t = e.touches[0];
                start = { x: t.clientX, y: t.clientY, time: Date.now(), target: t.target };
                forwardTouchToMouse(t, 'mousedown', start.target);
            }
        }, { passive: true });

        document.addEventListener('touchmove', function (e) {
            if (start && e.touches && e.touches.length > 0) {
                forwardTouchToMouse(e.touches[0], 'mousemove', start.target);
            }
        }, { passive: true });

        document.addEventListener('touchend', function (e) {
            if (!start) return;
            var t = e.changedTouches && e.changedTouches[0];
            if (t) {
                var dx = t.clientX - start.x;
                var dy = t.clientY - start.y;
                var isTap = Math.sqrt(dx * dx + dy * dy) <= TAP_SLOP &&
                    (Date.now() - start.time) <= TAP_TIMEOUT;
                forwardTouchToMouse(t, 'mouseup', start.target);
                if (isTap) forwardTouchToMouse(t, 'click', start.target);
            }
            start = null;
            if (e.cancelable) e.preventDefault();
        }, { passive: false });

        document.addEventListener('touchcancel', function () { start = null; }, { passive: true });
    """.trimIndent()

    private fun buildScript(touchPolyfill: Boolean, fps: Int): String {
        val core = coreScriptJs.replace("__FPS__", fps.coerceAtLeast(0).toString())
        val body = if (touchPolyfill) core + "\n\n" + touchPolyfillJs else core
        return "<script id=\"webpaper-injected-script\">\n(function () {\n" + body + "\n})();\n</script>"
    }

    /**
     * Removes the blocks [prepareHtml] injects. Used to clean wallpapers saved by older versions of
     * the app (which stored the injected code) and to make [prepareHtml] repeatable.
     */
    fun stripInjected(html: String): String =
        html.replace(injectedStyleRegex, "").replace(injectedScriptRegex, "")

    /**
     * Makes arbitrary user HTML behave like a wallpaper: full-bleed layout, a viewport tag, a
     * frame-rate cap, an optional touch-to-mouse bridge and no scrollbars. Idempotent:
     * `prepareHtml(prepareHtml(x)) == prepareHtml(x)`.
     *
     * The helpers are injected at the very start of `<head>` so they run before any page script.
     */
    fun prepareHtml(rawHtml: String, touchPolyfill: Boolean = true, fps: Int = 60): String {
        var content = stripInjected(rawHtml).trim()

        // A bare snippet (no <html>) gets wrapped into a complete document.
        if (!content.contains("<html", ignoreCase = true)) {
            content = "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n" +
                "<title>WebPaper</title>\n</head>\n<body>\n" + content + "\n</body>\n</html>"
        }

        // Make sure there is a <head> to hold the helpers.
        var head = headOpenRegex.find(content)
        if (head == null) {
            val html = htmlOpenRegex.find(content)
            if (html != null) {
                val at = html.range.last + 1
                content = content.substring(0, at) + "\n<head></head>" + content.substring(at)
                head = headOpenRegex.find(content)
            }
        }
        val insertAt = if (head != null) head.range.last + 1 else 0

        val viewport = if (viewportRegex.containsMatchIn(content)) "" else "\n" + VIEWPORT_META
        val block = "\n" + injectedCss + "\n" + buildScript(touchPolyfill, fps) + viewport

        return content.substring(0, insertAt) + block + content.substring(insertAt)
    }
}
