import * as esbuild from "esbuild";
import { spawn, ChildProcess } from "child_process";
import * as fs from "fs/promises";
import * as path from "path";

interface ProcessOptions {
    rootDir: string;
    entryPoints: string[];
    outDir: string;
    production: boolean;
}

async function processStatic(): Promise<void> {
    const rootDir = "viewer";
    const outDir = "app/src/main/assets/viewer";
    const outDirDebug = "app/src/debug/assets/viewer";

    await commandLine(getCommand("node_modules/.bin/eslint"), ".");

    await processScripts({
        rootDir,
        entryPoints: ["js/index.ts", "js/worker.ts"],
        outDir,
        production: true,
    });
    await processScripts({
        rootDir,
        entryPoints: ["js/index.ts", "js/worker.ts"],
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

    const pdfJsAssets = ["cmaps", "iccs", "standard_fonts", "wasm"];
    for (const asset of pdfJsAssets) {
        await fs.cp(path.join("node_modules/pdfjs-dist", asset), path.join(outDir, asset), {recursive: true});
    }
}

function getCommand(command: string, winExt: string = "cmd"): string {
    return path.resolve(globalThis.process.platform === "win32" ? `${command}.${winExt}` : command);
}

function commandLine(command: string, ...args: string[]): Promise<void> {
    return new Promise((resolve, reject) => {
        const subprocess: ChildProcess = spawn(command, args, { shell: false, stdio: "inherit" });
        subprocess.on("close", (code: number | null) => code === 0 ? resolve() : reject());
    });
}

async function processScripts(options: ProcessOptions): Promise<void> {
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
        loader: {
            ".ts": "ts",
            ".js": "js"
        },
        tsconfigRaw: {
            compilerOptions: {
                target: "ES2020",
                module: "ES2020",
                moduleResolution: "node",
                lib: ["ES2020", "DOM", "DOM.Iterable"],
                strict: true,
                skipLibCheck: true
            }
        }
    });
}

async function processStyles(options: ProcessOptions): Promise<void> {
    const entryPoints = options.entryPoints.map((filepath) => path.join(options.rootDir, filepath));
    await esbuild.build({
        entryPoints,
        bundle: true,
        outdir: options.outDir,
        minify: options.production,
    });
}

async function processHtml(options: ProcessOptions): Promise<void> {
    await fs.mkdir(options.outDir, { recursive: true });
    for (const entryPoint of options.entryPoints) {
        await fs.copyFile(path.join(options.rootDir, entryPoint), path.join(options.outDir, entryPoint));
    }
}

processStatic().catch(console.error);
