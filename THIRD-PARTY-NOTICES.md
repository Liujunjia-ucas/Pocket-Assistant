# Third-party notices / 第三方声明

The root MIT license covers project-owned code and documentation. It does not replace upstream licenses, transfer trademarks, or grant rights in material a user later imports.

## Bundled renderer

| Component | Version | Upstream | License text included |
|---|---|---|---|
| KaTeX, distributed browser JavaScript, CSS and WOFF2 fonts | 0.19.1 | https://github.com/KaTeX/KaTeX / https://www.npmjs.com/package/katex/v/0.19.1 | MIT, `app/src/main/assets/renderer/KATEX-LICENSE.txt` |
| Marked, browser UMD JavaScript | 15.0.12 | https://github.com/markedjs/marked / https://www.npmjs.com/package/marked/v/15.0.12 | MIT, `app/src/main/assets/renderer/MARKED-LICENSE.txt` |

Vendored file hashes and package provenance are recorded in `app/src/main/assets/renderer/vendor-manifest.json`. The audit verifies these hashes. Project-specific `renderer.js`, `renderer.css` and `index.html` are integration code, not upstream license replacements. Retain the upstream texts and copyright notices in source and binary distributions; both are packaged inside Android assets.

## Build and test tools, not included as binaries

Android Gradle Plugin 8.2.2, Gradle 8.7, JDK 17, Android SDK and platform tools are obtained independently from their maintainers. Their distribution and service terms apply. The JVM regression tests use JSON-java 20240303 (`org.json:json`), Apache-2.0; users obtain the test dependency separately. No dependency JAR, executable, Android framework implementation or SDK is included in the source export.

## User-installed services

Codex CLI, Tailscale, Clash Verge, API/model platforms, phone speech services and Volcengine speech are not redistributed in this repository. Compatibility code is not an endorsement, trademark license, entitlement to service, or permission to redistribute their model/voice assets. The optional Windows proxy helper is project integration code, not a copy of Clash Verge's application.

## Excluded artwork and speech

The community source and its verified release resources contain no third-party character artwork, animation, wallpaper, vendor Logo, bundled speech model/native ASR library, cloned voice or computer speech runtime. See RISK-NOTICE.md before adding any such content. Do not copy an old private APK or original asset folder into a public Release.
