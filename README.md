Simple Android PDF viewer based on pdf.js and content providers. The app
doesn't require any permissions. The PDF stream is fed into the sandboxed
WebView without giving it access to the network, files, content providers or
any other data.

Content-Security-Policy is used to enforce that the JavaScript and styling
properties within the WebView are entirely static content from the APK assets
along with blocking custom fonts since pdf.js handles rendering those itself.

It reuses the hardened Chromium rendering stack while only exposing a tiny
subset of the attack surface compared to actual web content. The PDF rendering
code itself is memory safe with dynamic code evaluation disabled, and even if
an attacker did gain code execution by exploiting the underlying web rendering
engine, they're within the Chromium renderer sandbox with less access than it
would have within the browser.

## Tests

### Android instrumentation tests

Requires a connected device or running emulator.

```sh
./gradlew connectedAndroidTest
```

To run a single test:

```sh
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.grapheneos.pdfviewer.test.PdfViewerLaunchTest
```

### JavaScript unit tests

Requires Node.js 20+. Make sure modules are installed:

```sh
npm install
```

Run the tests:

```sh
npm test
```