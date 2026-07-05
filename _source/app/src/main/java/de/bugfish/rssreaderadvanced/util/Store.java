package de.bugfish.rssreaderadvanced.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Opens a Google Play listing, preferring the Play Store app, else the web. */
public final class Store {

    private Store() {
    }

    public static void openPlayListing(Context context, String packageName) {
        try {
            Intent market = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + packageName));
            market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(market);
        } catch (Exception e) {
            try {
                Intent web = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + packageName));
                web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(web);
            } catch (Exception ignored) {
            }
        }
    }
}
