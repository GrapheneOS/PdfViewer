import esbuild from "esbuild";
import { spawn } from "node:child_process";
import fs from "node:fs/promises";
import path from "node:path";

/**
 * @typedef ProcessOptions
 * @property {string} rootDir
 * @property {string[]} entryPoints
 * @property {string} outDir
 * @property {boolean} production
 */

async function processStatic() {
    const rootDir = "viewer";
    const outDir = "app/src/main/assets/viewer";
    const outDirDebug = "app/src/debug/assets/viewer";

    await commandLine(getCommand("node_modules/.bin/eslint"), ".");

    await processScripts({
        rootDir,
        entryPoints: ["js/index.js", "js/worker.js"],
        outDir,
        production: true,
    });
    await processScripts({
        rootDir,
        entryPoints: ["js/index.js", "js/worker.js"],
        outDir: outDirDebug,
        production: false,
    });
    await processStyles({
        rootDir,
        entryPoints: ["main.css"],
        outDir,
        production: true,
    });
    await processHtml({
        rootDir,
        entryPoints: ["index.html"],
        outDir,
        production: true,
    });
}

/**
 * @param {string} command
 * @param {string} winExt
 * @returns {string}
 */
function getCommand(command, winExt = "cmd") {
    return path.resolve(globalThis.process.platform === "win32" ? `${command}.${winExt}` : command);
}

/**
 * @param {string} command
 * @param  {...string} args
 * @returns {Promise<void>}
 */
function commandLine(command, ...args) {
    return new Promise((resolve, reject) => {
        const subprocess = spawn(command, args, { shell: false, stdio: "inherit" });
        subprocess.on("close", (code) => code === 0 ? resolve() : reject());
    });
}

/**
 * @param {ProcessOptions} options
 */
async function processScripts(options) {
    const entryPoints = options.entryPoints.map((filepath) => path.join(options.rootDir, filepath));
    await esbuild.build({
        entryPoints,
        bundle: true,
        format: "esm",
        platform: "browser",
        target: "chrome133",
        outdir: path.join(options.outDir, "js"),
        minify: options.production,
        sourcemap: options.production ? false : "inline",
    });
}

/**
 * @param {ProcessOptions} options
 */
async function processStyles(options) {
    const entryPoints = options.entryPoints.map((filepath) => path.join(options.rootDir, filepath));
    await esbuild.build({
        entryPoints,
        bundle: true,
        outdir: options.outDir,
        minify: options.production,
    });
}

/**
 * @param {ProcessOptions} options
 */
async function processHtml(options) {
    await fs.mkdir(options.outDir, { recursive: true });
    for (const entryPoint of options.entryPoints) {
        await fs.copyFile(path.join(options.rootDir, entryPoint), path.join(options.outDir, entryPoint));
    }
}

await processStatic();
