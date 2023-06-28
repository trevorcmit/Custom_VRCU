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

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONObject;

public class SearchItem implements Parcelable {

    public static final Creator<SearchItem> CREATOR = new Creator<SearchItem>() {

        @Override
        public SearchItem createFromParcel(final Parcel in) {
            return new SearchItem(in);
        }

        @Override
        public SearchItem[] newArray(final int size) {
            return new SearchItem[size];
        }
    };

    String mName;
    String mLink;

    public SearchItem(final Parcel in) {
        this.mName = in.readString();
        this.mLink = in.readString();
    }

    public SearchItem(final JSONObject jsonObject) {
        this.mName = jsonObject.optString("title");
        this.mLink = jsonObject.optString("link");
    }

    @Override public int describeContents() {
        return 0;
    }

    @Override public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeString(this.mName);
        dest.writeString(this.mLink);
    }

    public String getName() {
        return mName;
    }

    public void setName(final String name) {
        mName = name;
    }

    public String getLink() {
        return mLink;
    }

    public void setLink(final String link) {
        mLink = link;
    }
}