package android.print;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import java.io.File;

/** Writes a WebView print document straight to a PDF file (the print callbacks are package-private). */
public class PdfPrinter {
    public interface Done { void ok(File file); void fail(String why); }

    public static void write(final PrintDocumentAdapter adapter, final PrintAttributes attrs, final File out, final Done done) {
        adapter.onLayout(null, attrs, null, new PrintDocumentAdapter.LayoutResultCallback() {
            @Override public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                try {
                    final ParcelFileDescriptor fd = ParcelFileDescriptor.open(out,
                        ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_READ_WRITE);
                    adapter.onWrite(new PageRange[] { PageRange.ALL_PAGES }, fd, new CancellationSignal(), new PrintDocumentAdapter.WriteResultCallback() {
                        @Override public void onWriteFinished(PageRange[] pages) {
                            try { fd.close(); } catch (Exception ignored) { }
                            done.ok(out);
                        }
                        @Override public void onWriteFailed(CharSequence error) {
                            try { fd.close(); } catch (Exception ignored) { }
                            done.fail(error == null ? "write failed" : error.toString());
                        }
                    });
                } catch (Exception e) { done.fail(e.getMessage()); }
            }
            @Override public void onLayoutFailed(CharSequence error) { done.fail(error == null ? "layout failed" : error.toString()); }
        }, null);
    }
}
