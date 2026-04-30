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
