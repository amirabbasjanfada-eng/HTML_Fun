package com.example.web

data class Library(val name: String, val snippet: String)

/**
 * One-tap snippets that add popular libraries (newest release from a CDN) to a wallpaper. `@latest`
 * floats to the newest version; replace it with a fixed version number to freeze a wallpaper.
 */
object Libraries {

    val all: List<Library> = listOf(
        Library(
            "Three.js",
            """
            <script type="importmap">
              { "imports": {
                "three": "https://cdn.jsdelivr.net/npm/three@latest/build/three.module.js",
                "three/addons/": "https://cdn.jsdelivr.net/npm/three@latest/examples/jsm/"
              } }
            </script>
            <!-- then: <script type="module"> import * as THREE from 'three'; ... </script> -->
            """.trimIndent()
        ),
        Library("p5.js", """<script src="https://cdn.jsdelivr.net/npm/p5@latest/lib/p5.min.js"></script>"""),
        Library("GSAP", """<script src="https://cdn.jsdelivr.net/npm/gsap@latest/dist/gsap.min.js"></script>"""),
        Library("PixiJS", """<script src="https://cdn.jsdelivr.net/npm/pixi.js@latest/dist/pixi.min.js"></script>"""),
        Library("D3", """<script src="https://cdn.jsdelivr.net/npm/d3@latest/dist/d3.min.js"></script>"""),
        Library("Matter.js", """<script src="https://cdn.jsdelivr.net/npm/matter-js@latest/build/matter.min.js"></script>"""),
        Library("Tailwind", """<script src="https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4"></script>"""),
        Library(
            "ES module",
            """
            <script type="module">
              // Any npm package, newest version, as an ES module:
              // import confetti from 'https://esm.sh/canvas-confetti';
            </script>
            """.trimIndent()
        )
    )

    /** Inserts [library] before `</head>` (or at the top if there is no head). Never inserts twice. */
    fun insert(source: String, library: Library): String {
        val snippet = library.snippet.trim()
        if (source.contains(snippet)) return source
        val close = source.indexOf("</head>", ignoreCase = true)
        return if (close >= 0) {
            source.substring(0, close) + snippet + "\n" + source.substring(close)
        } else {
            snippet + "\n" + source
        }
    }
}
