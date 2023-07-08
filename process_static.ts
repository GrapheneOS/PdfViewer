import esbuild from "esbuild"
import { spawn } from "node:child_process"
import fs from "node:fs/promises"
import path from "node:path"

interface ProcessOptions {
    rootDir: string
    entryPoints: string[]
    outDir: string
    production: boolean
}

async function processStatic() {
    const rootDir = "viewer"
    const outDir = "app/src/main/assets/viewer"
    const outDirDebug = "app/src/debug/assets/viewer"

    await commandLine(getCommand("node_modules/.bin/tsc"), "-noEmit")

    await processScripts({
        rootDir,
        entryPoints: ["js/index.ts", "js/worker.ts"],
        outDir,
        production: true,
    })
    await processScripts({
        rootDir,
        entryPoints: ["js/index.ts", "js/worker.ts"],
        outDir: outDirDebug,
        production: false,
    })
    await processStyles({
        rootDir,
        entryPoints: ["main.css"],
        outDir,
        production: true,
    })
    await processHtml({
        rootDir,
        entryPoints: ["index.html"],
        outDir,
        production: true,
    })
}

function getCommand(command: string, winExt: string = "cmd"): string {
    return path.resolve(process.platform === "win32" ? `${command}.${winExt}` : command)
}

function commandLine(command: string, ...args: string[]) {
    return new Promise<void>((resolve, reject) => {
        const subprocess = spawn(command, args, { shell: false, stdio: "inherit" })
        subprocess.on("close", (code) => code === 0 ? resolve() : reject())
    })
}

async function processScripts(options: ProcessOptions) {
    const entryPoints = options.entryPoints.map((filepath) => path.join(options.rootDir, filepath))
    await esbuild.build({
        entryPoints,
        bundle: true,
        format: "esm",
        platform: "browser",
        target: "es2022",
        outdir: path.join(options.outDir, "js"),
        minify: options.production,
        sourcemap: options.production ? false : "inline",
    })
}

async function processStyles(options: ProcessOptions) {
    const entryPoints = options.entryPoints.map((filepath) => path.join(options.rootDir, filepath))
    await esbuild.build({
        entryPoints,
        bundle: true,
        outdir: options.outDir,
        minify: options.production,
    })
}

async function processHtml(options: ProcessOptions) {
    for (const entryPoint of options.entryPoints) {
        await fs.copyFile(path.join(options.rootDir, entryPoint), path.join(options.outDir, entryPoint))
    }
}

await processStatic()
