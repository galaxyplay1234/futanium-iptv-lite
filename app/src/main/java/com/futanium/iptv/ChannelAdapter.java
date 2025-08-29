package com.futanium.iptv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class ChannelAdapter extends ArrayAdapter<M3UParser.Item> {

    private final ImageLoader loader;
    private final int logoSizePx;

    public ChannelAdapter(Context ctx, List<M3UParser.Item> data) {
        super(ctx, 0, data);
        loader = new ImageLoader();
        // ~48dp → pixels
        float dp = 48f;
        logoSizePx = (int)TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                ctx.getResources().getDisplayMetrics());
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Row r;
        if (convertView == null) {
            r = new Row(getContext(), logoSizePx);
            convertView = r.root;
            convertView.setTag(r);
        } else {
            r = (Row) convertView.getTag();
        }
        M3UParser.Item it = getItem(position);

        r.title.setText(it.name != null ? it.name : "(sem nome)");

        r.logo.setImageDrawable(new ColorDrawable(Color.DKGRAY));
        if (it.logo != null && it.logo.length() > 0) {
            loader.load(it.logo, r.logo, logoSizePx);
        }
        return convertView;
    }

    static class Row {
        LinearLayout root;
        ImageView logo;
        TextView title;

        Row(Context ctx, int logoSize) {
            root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.HORIZONTAL);
            int pad = dp(ctx, 8);
            root.setPadding(pad, pad, pad, pad);
            root.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            logo = new ImageView(ctx);
            LinearLayout.LayoutParams lpLogo = new LinearLayout.LayoutParams(logoSize, logoSize);
            lpLogo.rightMargin = dp(ctx, 12);
            root.addView(logo, lpLogo);

            title = new TextView(ctx);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            root.addView(title);
        }

        private int dp(Context c, int d){
            return (int)TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, d, c.getResources().getDisplayMetrics());
        }
    }
}
