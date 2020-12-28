package org.grapheneos.pdfviewer;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class PdfDocumentAdapter extends PrintDocumentAdapter {

    private Context mContext;
    private String mPrintJobName;
    private Uri mUri;

    public PdfDocumentAdapter(Context ctx, String jobName, Uri uri) {
        this.mContext = ctx;
        this.mPrintJobName = jobName;
        this.mUri = uri;
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes, CancellationSignal cancellationSignal, LayoutResultCallback layoutResultCallback, Bundle bundle) {
        if (cancellationSignal.isCanceled()) {
            layoutResultCallback.onLayoutCancelled();
        } else {
            PrintDocumentInfo.Builder pdinfoBuilder = new PrintDocumentInfo.Builder(mPrintJobName);
            pdinfoBuilder.setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT);
            pdinfoBuilder.setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN);
            PrintDocumentInfo pdInfo = pdinfoBuilder.build();
            layoutResultCallback.onLayoutFinished(pdInfo, !newAttributes.equals(oldAttributes));
        }
    }

    @Override
    public void onWrite(PageRange[] pageRanges, ParcelFileDescriptor parcelFileDescriptor, CancellationSignal cancellationSignal, WriteResultCallback writeResultCallback) {
        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            inputStream = mContext.getContentResolver().openInputStream(mUri);;
            outputStream = new FileOutputStream(parcelFileDescriptor.getFileDescriptor());
            byte[] buffer = new byte[mContext.getResources().getInteger(R.integer.print_adapter_buffer_size)];

            int readSize = inputStream.read(buffer);
            while (readSize >= 0 && !cancellationSignal.isCanceled()) {
                outputStream.write(buffer, 0, readSize);
                readSize = inputStream.read(buffer);
            }

            if (cancellationSignal.isCanceled()) {
                writeResultCallback.onWriteCancelled();
            } else {
                writeResultCallback.onWriteFinished(new PageRange[] { PageRange.ALL_PAGES });
            }
        } catch (IOException ex) {
            writeResultCallback.onWriteFailed(ex.getMessage());
        } finally {
            try {
                if (outputStream != null)
                    outputStream.close();
                if (inputStream != null)
                    inputStream.close();
            } catch (IOException ignored) {

            }
        }
    }
}
