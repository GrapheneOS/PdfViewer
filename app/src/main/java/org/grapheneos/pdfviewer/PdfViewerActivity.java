package org.grapheneos.pdfviewer;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class PdfViewerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);

        if (savedInstanceState == null) {
            PdfViewerFragment fragment = PdfViewerFragment.newInstance();
            getSupportFragmentManager()
                    .beginTransaction()
                    .setPrimaryNavigationFragment(fragment)
                    .add(R.id.pdf_fragment_container, fragment)
                    .commit();
        }

    }
}