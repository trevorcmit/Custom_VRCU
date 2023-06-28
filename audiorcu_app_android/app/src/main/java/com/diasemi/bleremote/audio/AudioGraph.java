/*
 *******************************************************************************
 *
 * Copyright (C) 2017 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.audio;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.util.Date;

/**
 * Displays audio samples in a running graph.
 * Uses AChartEngine. More info: http://www.achartengine.org/
 */
public class AudioGraph {
    private static final String TAG = "AudioGraph";

    private static final int Y_MAX_VALUE = 32768;
    private static final int Y_MIN_MAX_VALUE = 2622; // minimum allowed max Y value
    private static final double DIVISOR = 327.68; // normalize samples to [-100..100]
    private static final int GRAPH_SKIP_SAMPLES = 40; // downsample audio data by skipping
    private static final int GRAPH_DURATION = 5; // seconds
    private static final int GRAPH_SAMPLES = GRAPH_DURATION * 16000 / GRAPH_SKIP_SAMPLES;
    private static final int MAX_UPDATE_DELAY = 100;
    // Number of labels based on max value (0, 5, 10, 15, ..., 100)
    private static final int NUM_LABELS[] = new int[] { 0, 1, 2, 3, 4, 3, 3, 4, 4, 3, 2, 4, 4,  4, 3, 3, 4,  4, 3, 4, 4 };
    private static final int MAX_ADJUST[] = new int[] { 0, 0, 0, 0, 0, 5, 0, 5, 0, 0, 0, 5, 0, -5, 5, 0, 0, -5, 0, 5, 0 };

    private int[] bufSamples = new int[GRAPH_SAMPLES]; // used as ring buffer
    private int bufStartPos; // start index of ring buffer
    private int numSamples;
    private Activity context;
    private GraphicalView mView;
    private XYMultipleSeriesDataset mDataset = new XYMultipleSeriesDataset();
    private XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer();
    private XYSeries mCurrentSeries;
    private int maxY;
    private boolean pendingGraphUpdate;
    private long lastGraphUpdate;

    /**
     * Constructor
     */
    public AudioGraph(Activity context) {
        Log.i(TAG, "Adding Audio Graph");

        this.context = context;
        DisplayMetrics metrics = new DisplayMetrics();
        context.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, metrics);

        mCurrentSeries = new XYSeries("Audio");
        mDataset.addSeries(mCurrentSeries);
        XYSeriesRenderer currentRenderer = new XYSeriesRenderer();
        currentRenderer.setColor(Color.parseColor("#347ab8"));
        currentRenderer.setLineWidth(3);
        mRenderer.addSeriesRenderer(currentRenderer);
        mRenderer.setChartTitle("");
        mRenderer.setShowLegend(false);
        mRenderer.setShowAxes(false);
        mRenderer.setMarginsColor(Color.WHITE);
        mRenderer.setBackgroundColor(Color.WHITE);
        mRenderer.setApplyBackgroundColor(true);
        mRenderer.setPanEnabled(false, false);
        mRenderer.setZoomEnabled(false, false);
        //mRenderer.setPanLimits(new double[] { 0, GRAPH_SAMPLES, -Y_MAX_VALUE, Y_MAX_VALUE });
        //mRenderer.setZoomLimits(new double[] { 0, GRAPH_SAMPLES, -Y_MAX_VALUE, Y_MAX_VALUE });
        mRenderer.setYAxisMax(Y_MIN_MAX_VALUE / DIVISOR);
        mRenderer.setYAxisMin(-Y_MIN_MAX_VALUE / DIVISOR);

        // AChartEngine doesn't provide an easy way to remove axis labels.
        // In order to hide the X-axis, we use negative values for the
        // bottom margin, so that the X-axis would be out of view.
        mRenderer.setShowLabels(true);
        mRenderer.setShowGrid(false);
        mRenderer.setLabelsTextSize(10 * dp);
        mRenderer.setXLabelsColor(Color.parseColor("#ffffff"));
        mRenderer.setXLabels(0);
        mRenderer.setYLabelsColor(0, Color.parseColor("#347ab8"));
        mRenderer.setYLabelsAlign(Paint.Align.RIGHT);
        mRenderer.setYLabelsVerticalPadding(-3 * dp); // center vertically to grid
        //mRenderer.setYLabelsPadding(5 * dp); // doesn't apply for custom labels (spaces used instead)
        mRenderer.setYLabels(0); // use custom labels
        mRenderer.setMargins(new int[] { /* top, left, bottom, right */ (int)(10 * dp), (int)(25 * dp), (int)(-10 * dp), 0 });

        // Initialise the graph with zeros
        for (int i = 0; i < bufSamples.length; i++) {
            bufSamples[i] = 0;
            mCurrentSeries.add(i, 0);
        }
    }

    public GraphicalView getView() {
        mView = ChartFactory.getLineChartView(context, mDataset, mRenderer);
        return mView;
    }


    public void start() {
        numSamples = 0;
    }

    public void stop() {
        refreshGraph(true);
    }

    /**
     * addSampleData: add more samples to the running view.
     * <p/>
     * NOTE: We currently only use one sample per GRAPH_SKIP_SAMPLES, so effectively
     * the audioGraph uses a downsampled version. This is to minimise processing.
     *
     * @param audioSamples audioSamples
     */
    public void addSampleData(final short audioSamples[]) {
        for (short sample : audioSamples) {
            if ((numSamples++ % GRAPH_SKIP_SAMPLES) == 0) {
                synchronized (this) {
                    bufSamples[bufStartPos++] = sample;
                    if (bufStartPos == bufSamples.length)
                        bufStartPos = 0;
                    refreshGraph(false);
                }
            }
        }
    }

    public synchronized void refreshGraph(boolean force) {
        if (mView == null)
            return;
        if (!force && pendingGraphUpdate)
            return;
        pendingGraphUpdate = true;

        // Create new series and calculate new max value
        XYSeries newSeries = new XYSeries(mCurrentSeries.getTitle());
        int max = -1;
        for (int i = 0; i < bufSamples.length; i++) {
            int sample = bufSamples[(bufStartPos + i) % bufSamples.length];
            max = Math.max(max, Math.abs(sample));
            newSeries.add(i, sample / DIVISOR);
        }
        mCurrentSeries = newSeries;

        int newMaxY = Math.max(max, Y_MIN_MAX_VALUE);
        if (maxY > newMaxY)
            maxY -= Math.max((maxY - newMaxY) / 10, 320); // smooth transition
        if (maxY <= newMaxY)
            maxY = newMaxY;

        long elapsed = new Date().getTime() - lastGraphUpdate;
        if (!force && elapsed < MAX_UPDATE_DELAY)
            mView.postDelayed(mUpdateView, MAX_UPDATE_DELAY - elapsed);
        else
            mView.post(mUpdateView);
    }

    /**
     * The Runnable mUpdateView is used to update the view. The View must be
     * updated in the UI thread, and this is done by the view.post() function.
     */
    private Runnable mUpdateView = new Runnable() {

        @Override
        public void run() {
            repaint();
            lastGraphUpdate = new Date().getTime();
            pendingGraphUpdate = false;
        }
    };

    private void repaint() {
        double maxYNorm = maxY / DIVISOR + 2.5;
        int maxLabel = (int) Math.floor(maxYNorm / 5) * 5; // nearest multiple of 5
        int index = maxLabel / 5;
        maxLabel += MAX_ADJUST[index];
        // Add labels
        mRenderer.clearYTextLabels();
        mRenderer.addYTextLabel(0, "0   ");
        for (int i = 1; i <= NUM_LABELS[index]; ++i) {
            int val = i * maxLabel / NUM_LABELS[index];
            mRenderer.addYTextLabel(val, val + "   ");
            mRenderer.addYTextLabel(-val, val + "   ");
        }
        // Set min, max value
        if (maxLabel == 100)
            maxYNorm = 100;
        mRenderer.setYAxisMin(-maxYNorm);
        mRenderer.setYAxisMax(maxYNorm);
        // Update
        mDataset.clear();
        mDataset.addSeries(mCurrentSeries);
        mView.repaint();
    }
}
