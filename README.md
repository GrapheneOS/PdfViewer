Simple Android PDF viewer based on pdf.js and content providers. The app
doesn't require any permissions. The PDF stream is fed into the sandboxed
WebView without giving it access to content or files. Content-Security-Policy
is used to enforce that the JavaScript and styling properties within the
WebView are entirely static content from the apk assets. It reuses the hardened
Chromium rendering stack while only exposing a tiny subset of the attack
surface compared to actual web content. The PDF rendering code itself is memory
safe with dynamic code evaluation disabled, and even if an attacker did gain
code execution by exploiting the underlying web rendering engine, they're
within the Chromium renderer sandbox with no access to the network (unlike a
browser), files, or other content.
