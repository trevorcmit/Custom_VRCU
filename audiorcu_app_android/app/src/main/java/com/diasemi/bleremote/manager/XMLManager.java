/*
 *******************************************************************************
 *
 * Copyright (C) 2017 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.manager;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.Space;

import com.diasemi.bleremote.R;
import com.diasemi.bleremote.model.KeyButton;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

public class XMLManager {
    private static final String TAG = XMLManager.class.getSimpleName();

    private static final int BUTTON_MARGIN = 5;

    public static class KeyLayout {
        public String name;
        public String ptt;
        public int code;
        public KeyButton[] keys;
        public GridLayout gridLayout;
    }

    private static HashMap<Integer, KeyLayout> keyLayouts;

    public static Map<Integer, KeyLayout> readKeyLayouts(final Context context) {
        if (keyLayouts == null)
            keyLayouts = new HashMap<>();
        else
            return keyLayouts;
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            String expression = "//remote";
            AssetManager assetManager = context.getAssets();
            InputStream inputStream = assetManager.open("remotekeys.xml");
            InputSource inputSource = new InputSource(inputStream);
            NodeList nodes = (NodeList) xpath.evaluate(expression, inputSource, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); i++) {
                KeyLayout keyLayout = new KeyLayout();
                keyLayout.name = xpath.evaluate("@name", nodes.item(i));
                keyLayout.ptt = xpath.evaluate("@ptt", nodes.item(i));
                keyLayout.code = Integer.parseInt(xpath.evaluate("@code", nodes.item(i)).replace("0x", ""), 16);
                keyLayouts.put(keyLayout.code, keyLayout);
            }
        } catch (Exception e) {
            Log.e(TAG, "readKeyLayouts", e);
        }
        return keyLayouts;
    }

    public static void setupRemote(final Activity context, final KeyLayout keyLayout, final ViewGroup keyLayoutParentView) {
        try {
            // Check if layout is already initialized
            if (keyLayout.gridLayout != null) {
                keyLayoutParentView.removeAllViews();
                ViewGroup gridLayoutParent = (ViewGroup) keyLayout.gridLayout.getParent();
                if (gridLayoutParent != null)
                    gridLayoutParent.removeAllViews();
                keyLayoutParentView.addView(keyLayout.gridLayout);
                return;
            }

            // Create layout
            LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            context.getWindowManager().getDefaultDisplay().getMetrics(metrics);
            float dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, metrics);

            keyLayoutParentView.removeAllViews();
            keyLayout.gridLayout = (GridLayout) layoutInflater.inflate(R.layout.remote_control_key_layout, keyLayoutParentView, false);

            // Find remote XML
            XPath xpath = XPathFactory.newInstance().newXPath();
            AssetManager assetManager = context.getAssets();
            InputStream inputStream = assetManager.open("remotekeys.xml");
            InputSource inputSource = new InputSource(inputStream);
            String expression = "//remote[@name='" + keyLayout.name + "']";
            NodeList nodes = (NodeList) xpath.evaluate(expression, inputSource, XPathConstants.NODESET);
            Node remote = nodes.item(0);

            // Remote properties
            String value;
            value = xpath.evaluate("@rows", remote);
            int rows = value.isEmpty() ? 0 : Integer.parseInt(value);
            value = xpath.evaluate("@cols", remote);
            int cols = value.isEmpty() ? 0 : Integer.parseInt(value);
            value = xpath.evaluate("@dw", remote).replace("dp", "");
            int defaultWidth = value.isEmpty() ? context.getResources().getDimensionPixelSize(R.dimen.btn_width) : (int) (Integer.parseInt(value) * dp);
            if (cols != 0) {
                int addedWidth = (cols + 1) * 2 * BUTTON_MARGIN + keyLayoutParentView.getPaddingLeft() + keyLayoutParentView.getPaddingRight();
                if (defaultWidth * cols + addedWidth > keyLayoutParentView.getWidth()) {
                    defaultWidth = (keyLayoutParentView.getWidth() - addedWidth) / cols;
                    Log.d(TAG, "Layout won't fit. Setting default width to " + defaultWidth);
                }
            }
            value = xpath.evaluate("@dh", remote).replace("dp", "");
            int defaultHeight = value.isEmpty() ? context.getResources().getDimensionPixelSize(R.dimen.btn_height) : (int) (Integer.parseInt(value) * dp);
            if (rows != 0) {
                int addedHeight = (rows + 1) * 2 * BUTTON_MARGIN + keyLayoutParentView.getPaddingTop() + keyLayoutParentView.getPaddingBottom();
                if (defaultHeight * rows + addedHeight > keyLayoutParentView.getHeight()) {
                    defaultHeight = (keyLayoutParentView.getHeight() - addedHeight) / rows;
                    Log.d(TAG, "Layout won't fit. Setting default height to " + defaultHeight);
                }
            }
            value = xpath.evaluate("@bw", remote).replace("dp", "");
            int buttonWidth = value.isEmpty() ? defaultWidth : (int) (Integer.parseInt(value) * dp);
            value = xpath.evaluate("@bh", remote).replace("dp", "");
            int buttonHeight = value.isEmpty() ? defaultHeight : (int) (Integer.parseInt(value) * dp);
            value = xpath.evaluate("@sw", remote).replace("dp", "");
            int spaceWidth = value.isEmpty() ? defaultWidth : (int) (Integer.parseInt(value) * dp);
            value = xpath.evaluate("@sh", remote).replace("dp", "");
            int spaceHeight = value.isEmpty() ? defaultHeight : (int) (Integer.parseInt(value) * dp);
            Log.d(TAG, String.format("Remote [%s]: rows=%d, cols=%d, dw=%d, dh=%d, bw=%d, bh=%d, sw=%d, sh=%d",
                    keyLayout.name, rows, cols, defaultWidth, defaultHeight, buttonWidth, buttonHeight, spaceWidth, spaceHeight));

            // Buttons
            nodes = (NodeList) xpath.evaluate("key", remote, XPathConstants.NODESET);
            int nrNodes = nodes.getLength();
            keyLayout.keys = new KeyButton[nrNodes];
            for (int i = 0; i < nrNodes; i++) {
                String keyName = xpath.evaluate("@name", nodes.item(i));
                int code = Integer.parseInt(nodes.item(i).getTextContent().replace("0x", "").replace(" ", ""), 16);
                boolean modifier = xpath.evaluate("@mod", nodes.item(i)).equals("true");
                boolean disabled = xpath.evaluate("@disabled", nodes.item(i)).equals("true");
                boolean hide = xpath.evaluate("@hide", nodes.item(i)).equals("true");
                Button button = (Button) layoutInflater.inflate(R.layout.view_remote_control_button, keyLayout.gridLayout, false);
                button.setText(keyName);
                button.setEnabled(!disabled);
                keyLayout.keys[i] = new KeyButton(button, keyName, !modifier ? code : code & 0xff, !modifier ? 0 : (code >> 8) & 0xff);
                if (!hide)
                    keyLayout.gridLayout.addView(keyLayout.keys[i].getButton(), createGridLayoutParams(keyLayout.gridLayout, xpath, nodes.item(i), buttonWidth, buttonHeight, dp, keyName));
            }

            // Space specified in XML
            nodes = (NodeList) xpath.evaluate("space", remote, XPathConstants.NODESET);
            nrNodes = nodes.getLength();
            for (int i = 0; i < nrNodes; i++)
                keyLayout.gridLayout.addView(new Space(context), createGridLayoutParams(keyLayout.gridLayout, xpath, nodes.item(i), spaceWidth, spaceHeight, dp, null));

            // Additional space to ensure number of rows/columns is correct
            if (rows != 0 || cols != 0) {
                for (int i = 1; i <= Math.max(rows, cols); i++)
                    keyLayout.gridLayout.addView(new Space(context), createGridLayoutParams(keyLayout.gridLayout, Math.min(i, rows), Math.min(i, cols), 1, 1, spaceWidth, spaceHeight));
            }

            keyLayoutParentView.addView(keyLayout.gridLayout);
        } catch (Exception e) {
            Log.e(TAG, "setupRemote", e);
        }
    }

    private static GridLayout.LayoutParams createGridLayoutParams(GridLayout gridLayout, XPath xpath, Node node, int defaultWidth, int defaultHeight, float dp, String log) throws Exception {
        String value;
        int row = Integer.parseInt(xpath.evaluate("@row", node));
        int col = Integer.parseInt(xpath.evaluate("@col", node));
        value = xpath.evaluate("@rows", node);
        int rows = value.isEmpty() ? 1 : Integer.parseInt(value);
        value = xpath.evaluate("@cols", node);
        int cols = value.isEmpty() ? 1 : Integer.parseInt(value);
        value = xpath.evaluate("@w", node).replace("dp", "");
        int width = value.isEmpty() ? defaultWidth : (int) (Integer.parseInt(value) * dp);
        value = xpath.evaluate("@h", node).replace("dp", "");
        int height = value.isEmpty() ? defaultHeight : (int) (Integer.parseInt(value) * dp);
        if (log != null)
            Log.d(TAG, String.format("Key [%s]: r=%d, c=%d, rs=%d, cs=%d, w=%d, h=%d", log, row, col, rows, cols, width, height));
        return createGridLayoutParams(gridLayout, row, col, rows, cols, width, height);
    }

    private static GridLayout.LayoutParams createGridLayoutParams(GridLayout gridLayout, int row, int col, int rows, int cols, int width, int height) {
        GridLayout.LayoutParams layoutParams = new GridLayout.LayoutParams(gridLayout.getLayoutParams());
        layoutParams.rowSpec = GridLayout.spec(row, rows, GridLayout.CENTER);
        layoutParams.columnSpec = GridLayout.spec(col, cols, GridLayout.CENTER);
        layoutParams.width = width;
        layoutParams.height = height;
        layoutParams.setMargins(BUTTON_MARGIN, BUTTON_MARGIN, BUTTON_MARGIN, BUTTON_MARGIN);
        return layoutParams;
    }
}
