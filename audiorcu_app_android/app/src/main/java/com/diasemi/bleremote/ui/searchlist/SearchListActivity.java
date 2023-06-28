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

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.diasemi.bleremote.Constants;
import com.diasemi.bleremote.R;
import com.diasemi.bleremote.Utils;
import com.diasemi.bleremote.manager.JSONManager;

import java.util.ArrayList;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class SearchListActivity extends AppCompatActivity
        implements SearchCallback, OnItemClickListener {

    @InjectView(R.id.view_container)
    LinearLayout mViewContainer;
    @InjectView(R.id.progress_container)
    View mProgressView;
    @InjectView(R.id.status_message)
    TextView mStatusMessageView;
    @InjectView(android.R.id.list)
    ListView mListView;

    private String mSearchEngine;
    private String mSearchQuery;
    private boolean mFirstMatch;
    private boolean mFirstMatchOpened = false;
    private ArrayList<SearchItem> mSearchItems;

    // LIFE CYCLE METHOD(S)

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        ButterKnife.inject(this);
        Bundle extras = getIntent().getExtras();
        this.mSearchEngine = extras.getString(Constants.EXTRA_ENGINE);
        this.mSearchQuery = extras.getString(Constants.EXTRA_MESSAGE);
        this.mFirstMatch = extras.getBoolean(Constants.EXTRA_FIRST_MATCH);
        this.mListView.setOnItemClickListener(this);
        this.mListView.setEmptyView(findViewById(android.R.id.empty));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(true);
        if (Build.VERSION.SDK_INT >= 21)
            getWindow().setNavigationBarColor(getResources().getColor(R.color.navigation_bar_background));
        setSearchEngine(this.mSearchEngine);
    }

    // OPTIONS MENU METHOD(S)

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_search, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_search_google:
                setSearchEngine("Google");
                return true;
            case R.id.action_search_imdb:
                setSearchEngine("IMDB");
                return true;
            case R.id.action_search_youtube:
                setSearchEngine("YouTube");
                return true;
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // SearchCallback METHOD(S)

    @Override
    public void onSearchCompleted(final boolean success,
                                  final ArrayList<SearchItem> searchItems, final ArrayList<String> errorList) {
        Utils.showProgress(this, this.mViewContainer, this.mProgressView, false);
        this.mSearchItems = searchItems;
        if (!success) {
            this.mFirstMatch = false;
            String msg = "SEARCH ERROR";
            if (!errorList.isEmpty())
                msg += ": " + errorList.get(0);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        } else if (this.mFirstMatch && !mFirstMatchOpened && searchItems.size() > 0) {
            String url = searchItems.get(0).getLink();
            clickLink(url);
            mFirstMatchOpened = true;
            //finish();
        }
        SearchListAdapter adapter = new SearchListAdapter(this, searchItems);
        this.mListView.setAdapter(adapter);
    }

    @Override
    public void onItemClick(final AdapterView<?> adapterView, final View view,
                            final int position, final long id) {
        String url = this.mSearchItems.get(position).getLink();
        clickLink(url);
    }

    // PRIVATE METHOD(S)

    /**
     * setSearchEngine: set the search engine. Find the correct CX key. Note that the keys below are
     * for testing only. You should create your own search engines at https://www.google.com/cse/
     * <p/>
     * Note that this call will also start a new search
     *
     * @param searchEngine: search engine
     */
    private void setSearchEngine(final String searchEngine) {
        String apiKey = null;
        this.mSearchEngine = searchEngine;
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(this.mSearchEngine + " Search");
            actionBar.setSubtitle("\"" + this.mSearchQuery + "\"");
        }
        switch (searchEngine) {
            case "Google":
                apiKey = Constants.GOOGLE_KEY;
                break;
            case "IMDB":
                apiKey = Constants.IMDB_KEY;
                break;
            case "RottenTomatoes":
                apiKey = Constants.ROTTEN_TOMATOES_KEY;
                break;
            case "YouTube":
                apiKey = Constants.YOUTUBE_KEY;
                break;
            default:
                break;
        }
        Utils.showProgress(this, this.mViewContainer, this.mProgressView, true);
        this.mStatusMessageView.setText(R.string.text_searching);
        JSONManager.search(this, apiKey, this.mSearchQuery, this);
    }

    private void clickLink(final String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_activity_for_url_view, Toast.LENGTH_LONG).show();
        }
    }
}
