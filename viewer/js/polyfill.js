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
