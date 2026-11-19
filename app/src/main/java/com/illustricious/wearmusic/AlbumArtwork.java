package com.illustricious.wearmusic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.IOException;

final class AlbumArtwork {
    private static final int MAX_EDGE = 384;

    private AlbumArtwork() {}

    static Bitmap load(Context context, Uri track) {
        try (MediaMetadataRetriever metadata = new MediaMetadataRetriever()) {
            metadata.setDataSource(context, track);
            byte[] image = metadata.getEmbeddedPicture();
            if (image == null || Thread.currentThread().isInterrupted()) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(image, 0, image.length, options);
            int edge = Math.max(options.outWidth, options.outHeight);
            if (edge <= 0) return null;
            options.inSampleSize = 1;
            while (edge / options.inSampleSize > MAX_EDGE) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(image, 0, image.length, options);
        } catch (IOException | RuntimeException unavailable) {
            return null;
        }
    }
}
