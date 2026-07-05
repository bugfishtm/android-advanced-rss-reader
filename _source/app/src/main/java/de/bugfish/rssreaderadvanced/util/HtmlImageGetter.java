package de.bugfish.rssreaderadvanced.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Loads {@code <img>} sources for the in-app reader, asynchronously and
 * defensively. Each image URL is validated to http(s) ({@link Web#isHttpUrl}),
 * the response must be an image, the download is byte-capped, and the bitmap is
 * down-sampled. Any failure (missing/invalid/oversized/unreachable image) simply
 * leaves an empty placeholder — it never crashes the reader.
 *
 * <p>Note: loading these images reaches out to third-party servers and exposes
 * the device IP; it is only used when the user has explicitly enabled images.
 */
public class HtmlImageGetter implements Html.ImageGetter {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DIM = 2048;

    private final Context appContext;
    private final TextView target;

    public HtmlImageGetter(Context context, TextView target) {
        this.appContext = context.getApplicationContext();
        this.target = target;
    }

    @Override
    public Drawable getDrawable(String source) {
        final Placeholder placeholder = new Placeholder();
        if (!Web.isHttpUrl(source)) {
            return placeholder; // unsupported scheme -> render nothing
        }
        Bg.run(() -> download(source), bitmap -> {
            if (bitmap == null) return;
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            int maxW = target.getWidth() - target.getPaddingLeft() - target.getPaddingRight();
            if (maxW <= 0) {
                maxW = appContext.getResources().getDisplayMetrics().widthPixels;
            }
            if (w > maxW) {
                float scale = maxW / (float) w;
                w = maxW;
                h = Math.round(h * scale);
            }
            BitmapDrawable d = new BitmapDrawable(target.getResources(), bitmap);
            d.setBounds(0, 0, w, h);
            placeholder.setDrawable(d);
            placeholder.setBounds(0, 0, w, h);
            // Re-set the same text to force the layout to account for the image.
            target.setText(target.getText());
        });
        return placeholder;
    }

    private Bitmap download(String urlStr) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "RSSReaderAdvanced/1.0 (Android)");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            String type = conn.getContentType();
            if (type != null && !type.toLowerCase(Locale.US).startsWith("image/")) {
                return null; // not an image -> ignore
            }
            in = conn.getInputStream();
            byte[] bytes = readCapped(in);
            if (bytes == null) return null;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
            opts.inSampleSize = sampleSize(opts.outWidth, opts.outHeight);
            opts.inJustDecodeBounds = false;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
            if (conn != null) conn.disconnect();
        }
    }

    private static byte[] readCapped(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        long total = 0;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > MAX_BYTES) return null;
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static int sampleSize(int w, int h) {
        int sample = 1;
        while (w / sample > MAX_DIM || h / sample > MAX_DIM) {
            sample *= 2;
        }
        return sample;
    }

    /** A drawable whose content is filled in once the image has loaded. */
    private static final class Placeholder extends Drawable {
        private Drawable wrapped;

        void setDrawable(Drawable d) {
            this.wrapped = d;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (wrapped != null) wrapped.draw(canvas);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
