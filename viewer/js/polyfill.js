// Uint8Array.prototype.toHex
// Shipped in Chromium v140 (https://chromestatus.com/feature/6281131254874112)
if (!Uint8Array.prototype.toHex) {
    const hexNumbers = Array.from({ length: 256 }, (_, i) =>
        i.toString(16).padStart(2, "0")
    );
    Uint8Array.prototype.toHex = function () {
        return Array.from(this, num => hexNumbers[num]).join("");
    };
}

// Uint8Array.prototype.toBase64
// Shipped in Chromium v140 (https://chromestatus.com/feature/6281131254874112)
// Replicate bytesToString(bytes) from util.js
if (!Uint8Array.prototype.toBase64) {
    Uint8Array.prototype.toBase64 = function () {
        const length = this.length;
        const MAX_ARGUMENT_COUNT = 8192;
        if (length < MAX_ARGUMENT_COUNT) {
            return btoa(String.fromCharCode.apply(null, this));
        }
        const strBuf = [];
        for (let i = 0; i < length; i += MAX_ARGUMENT_COUNT) {
            const chunkEnd = Math.min(i + MAX_ARGUMENT_COUNT, length);
            const chunk = this.subarray(i, chunkEnd);
            strBuf.push(String.fromCharCode.apply(null, chunk));
        }
        return btoa(strBuf.join(""));
    };
}

// Uint8Array.fromBase64
// Shipped in Chromium v140 (https://chromestatus.com/feature/6281131254874112)
// Replicate stringToBytes(str) from util.js
if (!Uint8Array.fromBase64) {
    Uint8Array.fromBase64 = function (str) {
        const binary = atob(str);
        const length = binary.length;
        const bytes = new Uint8Array(length);
        for (let i = 0; i < length; i++) {
            bytes[i] = binary.charCodeAt(i) & 0xff;
        }
        return bytes;
    };
}

// Map.prototype.getOrInsertComputed
// Shipped in Chromium v145 (https://chromestatus.com/feature/5201653661827072)
if (!Map.prototype.getOrInsertComputed) {
    Map.prototype.getOrInsertComputed = function (key, callbackFunction) {
        if (!this.has(key)) {
            this.set(key, callbackFunction(key));
        }
        return this.get(key);
    };
}

// Math.sumPrecise
// Shipped in Chromium v147 (https://chromestatus.com/feature/4790090146643968)
// This is the pdf.js version previously included in their source
if (typeof Math.sumPrecise !== "function") {
    Math.sumPrecise = function (numbers) {
        return numbers.reduce((a, b) => a + b, 0);
    };
}
