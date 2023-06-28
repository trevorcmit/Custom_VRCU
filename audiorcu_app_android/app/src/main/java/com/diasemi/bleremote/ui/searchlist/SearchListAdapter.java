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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.diasemi.bleremote.R;

import java.util.List;

public class SearchListAdapter extends ArrayAdapter<SearchItem> {

    static class ViewHolder {

        SearchItemView mItemView;
    }

    private static LayoutInflater sInflater;

    private List<SearchItem> mList;

    public SearchListAdapter(final Context context, final List<SearchItem> list) {
        super(context, 0, list);
        sInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.mList = list;
    }

    @Override
    public int getCount() {
        return this.mList.size();
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        View view = convertView;
        ViewHolder viewHolder;
        if (view == null) {
            view = sInflater.inflate(R.layout.view_search_list_item, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.mItemView = (SearchItemView) view.findViewById(R.id.list_item);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }
        SearchItem item = getItem(position);
        if (item != null) {
            viewHolder.mItemView.setValues(item);
        }
        return view;
    }
}