/**
 * Does BFS traversal of all of the nodes in the outline tree to convert the tree so that the
 * nodes are of a simpler form. The simple outline nodes have the following structure:
 *
 * ```
 *  {
 *      t: String, // title
 *      p: int (-1 means unknown), // pageNumber
 *      c: Array of simple outline nodes, // children
 *  }
 * ```
 *
 * @param {Array} pdfJsOutline The root node of the outline tree as obtained by
 * pdfDoc.getOutline. This is assumed to be an ordered tree.
 *
 * @return {Promise} A promise that is resolved with an {Array} that contains
 * all the top-level nodes of the outline in simplified form
 */
export async function getSimplifiedOutline(pdfJsOutline, abortController, pdfDoc) {
    if (pdfJsOutline === undefined || pdfJsOutline === null || pdfJsOutline.length === 0) {
        return null;
    }

    const pageNumberPromises = [];
    const topLevelEntries = [];

    // Each item in this queue represents a PDF.js outline node with a
    // reference to an array of its children in the simplified node form.
    const outlineQueue = [{
        pdfJsChildren: pdfJsOutline,
        // No parents for at top/root, so it starts out as null for them.
        parentSimpleChildrenArray: null,
    }];

    while (outlineQueue.length > 0) {
        abortController.signal.throwIfAborted();

        const currentOutlinePayload = outlineQueue.shift();
        const parentChildrenArray = currentOutlinePayload.parentSimpleChildrenArray;
        const currentPdfJsChildren = currentOutlinePayload.pdfJsChildren;
        for (const pdfJsChild of currentPdfJsChildren) {
            abortController.signal.throwIfAborted();

            const simpleChild = {
                t: pdfJsChild.title,
                // The pageNumber is resolved later.
                p: -1,
                c: [],
            };

            if (parentChildrenArray !== null) {
                parentChildrenArray.push(simpleChild);
            } else {
                topLevelEntries.push(simpleChild);
            }

            if (pdfJsChild.items.length > 0) {
                outlineQueue.push({
                    pdfJsChildren: pdfJsChild.items,
                    parentSimpleChildrenArray: simpleChild.c,
                });
            }

            // Resolve the page number. Note that dest options can be a string
            // or an object according to the the PDF spec.
            const dest = (typeof pdfJsChild.dest === "string")
                ? await pdfDoc.getDestination(pdfJsChild.dest) : pdfJsChild.dest;
            if (Array.isArray(dest)) {
                const destRef = dest[0];
                if (typeof destRef === "object") {
                    pageNumberPromises.push(
                        pdfDoc.getPageIndex(destRef).then(function(index) {
                            simpleChild.p = parseInt(index) + 1;
                        }).catch(function(error) {
                            console.log("pdfDoc.getPageIndex error: " + error);
                            simpleChild.p = -1;
                        })
                    );
                } else {
                    simpleChild.p = Number.isInteger(destRef) ? destRef + 1 : -1;
                }
            }
        }
    }

    await Promise.all(pageNumberPromises);

    return topLevelEntries;
}
