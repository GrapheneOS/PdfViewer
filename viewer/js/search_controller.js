export class SearchController {
    constructor(getPdfDoc, getPageCallback, onNoResults) {
        this.getPdfDoc = getPdfDoc;
        this.getPageCallback = getPageCallback; // Function to render a specific page

        this.onNoResults = onNoResults; // Callback: () => {}
        this.matches = []; // Array of { pageNum, matchIdx, matchLen }
        this.currentMatchIndex = -1;
        this.searchAbortController = null;
        this.highlightOverlay = null; // Container for highlights
        this.activeTextLayer = null;
        this.activePage = 0;
        this.pageTextCache = {};
    }

    async find(query) {
        this.clear();
        if (!query) {
            return;
        }

        if (this.searchAbortController) {
            this.searchAbortController.abort();
        }
        this.searchAbortController = new AbortController();
        const signal = this.searchAbortController.signal;

        const pdfDoc = this.getPdfDoc();
        if (!pdfDoc) {
            return;
        }

        const numPages = pdfDoc.numPages;
        const lowerQuery = query.toLowerCase();

        // Search order detection: start at activePage, go to end, then wrap around to 1
        let searchOrder = [];
        const startPage = this.activePage > 0 ? this.activePage : 1;

        for (let i = startPage; i <= numPages; i++) {
            searchOrder.push(i);
        }
        for (let i = 1; i < startPage; i++) {
            searchOrder.push(i);
        }

        let firstMatchFound = false;
        let lastYieldTime = Date.now();

        for (const pageNum of searchOrder) {
            if (signal.aborted) {
                return;
            }

            // Yield control periodically to keep UI responsive
            const now = Date.now();
            if (now - lastYieldTime > 20) {
                await new Promise((resolve) => requestAnimationFrame(resolve));
                lastYieldTime = Date.now();
            }

            try {
                const lowerText = await this._extractText(pdfDoc, pageNum);

                let matchesOnPage = [];
                let startIndex = 0;
                let matchIdx = lowerText.indexOf(lowerQuery, startIndex);

                while (matchIdx !== -1) {
                    matchesOnPage.push({
                        pageNum: pageNum,
                        matchIdx: matchIdx,
                        matchLen: query.length
                    });
                    startIndex = matchIdx + query.length;
                    matchIdx = lowerText.indexOf(lowerQuery, startIndex);
                }

                if (matchesOnPage.length > 0) {
                    // Store the current match object before modification/sorting
                    const currentMatchBeforeSort = (this.currentMatchIndex !== -1 && this.currentMatchIndex < this.matches.length)
                        ? this.matches[this.currentMatchIndex]
                        : null;

                    this.matches.push(...matchesOnPage);
                    // Sort matches by page order to keep navigation logical
                    this.matches.sort((a, b) => {
                        if (a.pageNum !== b.pageNum) return a.pageNum - b.pageNum;
                        return a.matchIdx - b.matchIdx;
                    });

                    // Restore the correct index for the previously selected match
                    if (currentMatchBeforeSort) {
                        this.currentMatchIndex = this.matches.indexOf(currentMatchBeforeSort);
                    }

                    // If this is the *very first* match we found in this session, highlight it safely
                    if (!firstMatchFound) {
                        firstMatchFound = true;

                        // Find the index of the first match on the startPage (or closest to it)
                        // Because we sorted, we just need to find where our current page matches started
                        const firstMatchIndex = this.matches.findIndex(m => m.pageNum === pageNum);
                        if (firstMatchIndex !== -1) {
                            this.currentMatchIndex = firstMatchIndex;
                            this.showMatch(this.matches[this.currentMatchIndex]);
                        }
                    }


                }

            } catch (e) {
                console.error(`Error searching page ${pageNum}:`, e);
            }
        }

        if (this.matches.length === 0) {
            if (this.onNoResults) {
                this.onNoResults();
            }
        }
    }

    async _extractText(pdfDoc, pageNum) {
        if (this.pageTextCache[pageNum]) {
            return this.pageTextCache[pageNum];
        }

        const page = await pdfDoc.getPage(pageNum);
        const textContent = await page.getTextContent();
        const strings = textContent.items.map((item) => item.str);
        const originalText = strings.join("");
        const lowerText = originalText.toLowerCase();

        this.pageTextCache[pageNum] = lowerText;
        return lowerText;
    }

    findNext(findPrevious) {
        if (this.matches.length === 0) {
            return;
        }

        if (findPrevious) {
            this.currentMatchIndex--;
            if (this.currentMatchIndex < 0) {
                this.currentMatchIndex = this.matches.length - 1;
            }
        } else {
            this.currentMatchIndex++;
            if (this.currentMatchIndex >= this.matches.length) {
                this.currentMatchIndex = 0;
            }
        }

        this.showMatch(this.matches[this.currentMatchIndex]);
    }


    clear() {
        if (this.searchAbortController) {
            this.searchAbortController.abort();
            this.searchAbortController = null;
        }
        this.matches = [];
        this.currentMatchIndex = -1;
        this.removeHighlights();
    }

    showMatch(match) {
        let textLayer = this.activeTextLayer;
        if (textLayer && !textLayer.isConnected) {
            textLayer = null;
        }
        textLayer = textLayer || document.getElementById("text");

        let currentPage = this.activePage;
        if (typeof channel !== "undefined" && channel.getPage()) {
            currentPage = channel.getPage();
        }

        if (match.pageNum === currentPage && textLayer) {
            this.drawPageMatches(match.pageNum, textLayer);
        }

        this.getPageCallback(match.pageNum);
    }

    drawPageMatches(pageNum, textLayerDiv, skipScroll = false) {
        this.removeHighlights();
        this.activeTextLayer = textLayerDiv;
        this.activePage = pageNum;

        if (this.matches.length === 0) {
            return;
        }

        const pageMatches = this.matches.filter((m) => m.pageNum === pageNum);
        if (pageMatches.length === 0) {
            return;
        }

        if (!textLayerDiv) {
            return;
        }

        const container = document.createElement("div");
        container.className = "search-highlight-container";

        const spans = textLayerDiv.querySelectorAll("span");
        const currentMatch = (this.currentMatchIndex !== -1) ? this.matches[this.currentMatchIndex] : null;
        let selectedDiv = null;

        // Get the scale factor from CSS custom property
        const containerEl = document.getElementById("container");
        const scaleFactor = parseFloat(containerEl.style.getPropertyValue("--scale-factor")) || 1;

        let currentLen = 0;

        spans.forEach((span) => {
            const text = span.textContent;
            const spanLen = text.length;
            const spanStart = currentLen;
            const spanEnd = currentLen + spanLen;

            for (const match of pageMatches) {
                const matchStart = match.matchIdx;
                const matchEnd = match.matchIdx + match.matchLen;

                if (spanEnd > matchStart && spanStart < matchEnd) {
                    const startOffset = Math.max(0, matchStart - spanStart);
                    const endOffset = Math.min(spanLen, matchEnd - spanStart);

                    if (span.firstChild) {
                        const range = document.createRange();
                        range.setStart(span.firstChild, startOffset);
                        range.setEnd(span.firstChild, endOffset);

                        const clientRects = range.getClientRects();
                        const textLayerRect = textLayerDiv.getBoundingClientRect();

                        for (const rect of clientRects) {
                            const div = document.createElement("div");
                            div.className = "search-highlight";

                            if (match === currentMatch) {
                                div.classList.add("selected");
                                if (!selectedDiv) {
                                    selectedDiv = div;
                                }
                            }

                            // Calculate positions relative to text layer using current viewport positions
                            // and then normalize by the scale factor to get unscaled coordinates
                            const left = (rect.left - textLayerRect.left) / scaleFactor;
                            const top = (rect.top - textLayerRect.top) / scaleFactor;
                            const width = rect.width / scaleFactor;
                            const height = rect.height / scaleFactor;

                            div.style.left = left + "px";
                            div.style.top = top + "px";
                            div.style.width = width + "px";
                            div.style.height = height + "px";

                            container.appendChild(div);
                        }
                    }
                }
            }

            currentLen += spanLen;
        });

        textLayerDiv.appendChild(container);
        this.highlightOverlay = container;

        if (selectedDiv && !skipScroll) {
            selectedDiv.scrollIntoView({ block: "center", inline: "center" });
        }
    }

    removeHighlights() {
        if (this.highlightOverlay) {
            this.highlightOverlay.remove();
            this.highlightOverlay = null;
        }
    }
}
