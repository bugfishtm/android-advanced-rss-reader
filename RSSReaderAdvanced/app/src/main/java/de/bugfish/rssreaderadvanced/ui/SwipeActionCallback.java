package de.bugfish.rssreaderadvanced.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Generic swipe handler that paints a coloured background and an icon while the
 * row is dragged, then reports the swiped position + direction to a listener.
 */
public class SwipeActionCallback extends ItemTouchHelper.SimpleCallback {

    public interface Listener {
        void onSwiped(int position, int direction);
    }

    private final Listener listener;
    private final Drawable rightIcon;
    private final Drawable leftIcon;
    private final int rightColor;
    private final int leftColor;
    private final ColorDrawable background = new ColorDrawable();
    private final int iconMargin;

    public SwipeActionCallback(Context ctx, int swipeDirs,
                               int rightIconRes, int rightColorRes,
                               int leftIconRes, int leftColorRes,
                               Listener listener) {
        super(0, swipeDirs);
        this.listener = listener;
        this.rightIcon = tinted(ctx, rightIconRes);
        this.leftIcon = tinted(ctx, leftIconRes);
        this.rightColor = ContextCompat.getColor(ctx, rightColorRes);
        this.leftColor = ContextCompat.getColor(ctx, leftColorRes);
        this.iconMargin = Math.round(ctx.getResources().getDisplayMetrics().density * 20);
    }

    private static Drawable tinted(Context ctx, int res) {
        if (res == 0) return null;
        Drawable d = ContextCompat.getDrawable(ctx, res);
        if (d != null) {
            d = d.mutate();
            d.setTint(Color.WHITE);
        }
        return d;
    }

    @Override
    public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                          @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
        int pos = vh.getBindingAdapterPosition();
        if (pos != RecyclerView.NO_POSITION) {
            listener.onSwiped(pos, direction);
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                            @NonNull RecyclerView.ViewHolder vh, float dX, float dY,
                            int actionState, boolean isCurrentlyActive) {
        View item = vh.itemView;
        if (dX > 0) {
            background.setColor(rightColor);
            background.setBounds(item.getLeft(), item.getTop(),
                    item.getLeft() + (int) dX, item.getBottom());
            background.draw(c);
            drawIcon(c, item, rightIcon, true);
        } else if (dX < 0) {
            background.setColor(leftColor);
            background.setBounds(item.getRight() + (int) dX, item.getTop(),
                    item.getRight(), item.getBottom());
            background.draw(c);
            drawIcon(c, item, leftIcon, false);
        }
        super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
    }

    private void drawIcon(Canvas c, View item, Drawable icon, boolean atLeft) {
        if (icon == null) return;
        int iw = icon.getIntrinsicWidth();
        int ih = icon.getIntrinsicHeight();
        int top = item.getTop() + (item.getHeight() - ih) / 2;
        if (atLeft) {
            int left = item.getLeft() + iconMargin;
            icon.setBounds(left, top, left + iw, top + ih);
        } else {
            int right = item.getRight() - iconMargin;
            icon.setBounds(right - iw, top, right, top + ih);
        }
        icon.draw(c);
    }
}
