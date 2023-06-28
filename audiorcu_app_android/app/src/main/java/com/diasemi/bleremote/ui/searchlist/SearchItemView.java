/*
 *******************************************************************************
 *
 * Copyright (C) 2017 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.ui.searchlist;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.diasemi.bleremote.R;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class SearchItemView extends LinearLayout {

    @InjectView(R.id.labelTextView) TextView mTitleTextView;
    @InjectView(R.id.valueTextView) TextView mLinkTextView;

    public SearchItemView(final Context context) {
        super(context);
    }

    public SearchItemView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public SearchItemView(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        View rootView = inflater.inflate(R.layout.subview_search_list_item, this, true);
        ButterKnife.inject(this, rootView);
    }

    public void setValues(final SearchItem searchItem) {
        this.mTitleTextView.setText(searchItem.getName());
        this.mLinkTextView.setText(searchItem.getLink());
    }
}