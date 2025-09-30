// PDF.js 相关类型定义
export interface PDFDocument {
    numPages: number;
    getPage(pageNumber: number): Promise<PDFPage>;
    getOutline(): Promise<PDFOutlineItem[] | null>;
    getMetadata(): Promise<PDFMetadata>;
    getDestination(dest: string): Promise<any>;
    getPageIndex(ref: any): Promise<number>;
}

export interface PDFPage {
    getViewport(options: { scale: number; rotation: number }): PDFViewport;
    render(options: PDFRenderOptions): PDFRenderTask;
    streamTextContent(): any;
    rotate: number;
}

export interface PDFViewport {
    width: number;
    height: number;
}

export interface PDFRenderOptions {
    canvasContext: CanvasRenderingContext2D;
    viewport: PDFViewport;
}

export interface PDFRenderTask {
    promise: Promise<void>;
    cancel(): void;
}

export interface PDFOutlineItem {
    title: string;
    dest: string | any[];
    items: PDFOutlineItem[];
}

export interface PDFMetadata {
    info: any;
}

export interface SimpleOutlineNode {
    t: string; // title
    p: number; // pageNumber (-1 means unknown)
    c: SimpleOutlineNode[]; // children
}

export interface CachedPage {
    pageNumber: number;
    zoomRatio: number;
    orientationDegrees: number;
    canvas: HTMLCanvasElement;
    textLayerDiv: HTMLElement;
    pageWidth: number;
    pageHeight: number;
}

// Channel 接口定义（与Android通信）
export interface Channel {
    getPage(): number;
    getZoomRatio(): number;
    getMaxZoomRatio(): number;
    getMinZoomRatio(): number;
    getDocumentOrientationDegrees(): number;
    getZoomFocusX(): number;
    getZoomFocusY(): number;
    getMaxRenderPixels(): number;
    setZoomRatio(ratio: number): void;
    setDocumentOutline(outline: string | null): void;
    setNumPages(pages: number): void;
    setDocumentProperties(properties: string): void;
    setHasDocumentOutline(hasOutline: boolean): void;
    getPassword(): string;
    showPasswordPrompt(): void;
    invalidPassword(): void;
    onLoaded(): void;
}

// 全局变量声明
declare global {
    interface Window {
        channel: Channel;
        onRenderPage: (zoom: number) => void;
        isTextSelected: () => boolean;
        getDocumentOutline: () => void;
        abortDocumentOutline: () => void;
        toggleTextLayerVisibility: () => void;
        loadDocument: () => void;
        onresize: () => void;
    }
    
    var channel: Channel;
    var onRenderPage: (zoom: number) => void;
    var isTextSelected: () => boolean;
    var getDocumentOutline: () => void;
    var abortDocumentOutline: () => void;
    var toggleTextLayerVisibility: () => void;
    var loadDocument: () => void;
}
