package com.door43.translationstudio.panes.right;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.astuetz.PagerSlidingTabStrip;
import com.door43.translationstudio.R;
import com.door43.translationstudio.panes.right.tabs.NotesTabFragment;
import com.door43.translationstudio.panes.right.tabs.TermsTabFragment;
import com.door43.translationstudio.projects.Term;
import com.door43.translationstudio.util.StringFragmentKeySet;
import com.door43.translationstudio.util.TabbedViewPagerAdapter;
import com.door43.translationstudio.util.TabsFragmentAdapterNotification;
import com.door43.translationstudio.util.TranslatorBaseFragment;

import java.util.ArrayList;

/**
 * Created by joel on 8/26/2014.
 */
public class RightPaneFragment extends TranslatorBaseFragment {
    private ViewPager mViewPager;
    private PagerSlidingTabStrip mSlidingTabLayout;
    private TabbedViewPagerAdapter tabbedViewPagerAdapter;
    private ArrayList<StringFragmentKeySet> tabs = new ArrayList<StringFragmentKeySet>();
    private int mDefaultPage = 0;
    private int mSelectedTabColor = 0;
    private NotesTabFragment mNotesTab = new NotesTabFragment();
    private TermsTabFragment mTermsTab = new TermsTabFragment();
    private int mLayoutWidth = 0;
    private View mRootView;
    private static final String STATE_NOTES_SCROLL_X = "notes_scroll_x";
    private static final String STATE_NOTES_SCROLL_Y = "notes_scroll_y";
    private static final String STATE_SELECTED_TAB = "selected_tab_index";
    private int mSelectedTab = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        mRootView = inflater.inflate(R.layout.fragment_pane_right, container, false);

        if(savedInstanceState != null) {
            // restore scroll position
            mSelectedTab = savedInstanceState.getInt(STATE_SELECTED_TAB, 0);
            mDefaultPage = mSelectedTab;
            mNotesTab.setScroll(new Pair<>(savedInstanceState.getInt(STATE_NOTES_SCROLL_X), savedInstanceState.getInt(STATE_NOTES_SCROLL_Y)));
        }

        if(tabs.size() == 0) {
            // Tabs
            tabs.add(new StringFragmentKeySet(getResources().getString(R.string.label_translation_notes), mNotesTab));
            tabs.add(new StringFragmentKeySet(getResources().getString(R.string.key_terms), mTermsTab));
        }

        // ViewPager
        mViewPager = (ViewPager) mRootView.findViewById(R.id.rightViewPager);
        tabbedViewPagerAdapter = new TabbedViewPagerAdapter(getFragmentManager(), tabs);
        mViewPager.setAdapter(tabbedViewPagerAdapter);

        // Sliding tab layout
        mSlidingTabLayout = (PagerSlidingTabStrip) mRootView.findViewById(R.id.right_sliding_tabs);
        mSlidingTabLayout.setViewPager(mViewPager);
        if(mSelectedTabColor == 0) mSelectedTabColor = getResources().getColor(R.color.purple);
        mSlidingTabLayout.setIndicatorColor(mSelectedTabColor);
        mSlidingTabLayout.setDividerColor(Color.TRANSPARENT);

        // open the default page
        selectTab(mDefaultPage);

        if(mLayoutWidth != 0) {
            mRootView.setLayoutParams(new ViewGroup.LayoutParams(mLayoutWidth, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        return mRootView;
    }

    /**
     * Sets the selected tab index
     * @param i
     */
    public void selectTab(int i) {
        if(mViewPager != null) {
            if(tabs.size() > i && i >= 0) {
                // select the tab
                mViewPager.setCurrentItem(i);
                // notify the tab list adapter that it should reload
                ((TabsFragmentAdapterNotification)tabs.get(i).getFragment()).NotifyAdapterDataSetChanged();
            }
        } else {
            mDefaultPage = i;
        }
        mSelectedTab = i;
    }

    /**
     * Returns the currently selected tab index
     * @return
     */
    public int getSelectedTabIndex() {
        return mViewPager.getCurrentItem();
    }

    /**
     * Changes the color of the tabs selector
     * @param color a hexidecimal color value
     */
    public void setSelectedTabColor(int color) {
        if(mSlidingTabLayout != null) {
            mSlidingTabLayout.setIndicatorColor(color);
        } else {
            mSelectedTabColor = color;
        }
    }

    /**
     * Notifies the notes adapter that the dataset has changed
     */
    public void reloadNotesTab() {
        mNotesTab.NotifyAdapterDataSetChanged();
    }

    /**
     * Notifies the terms adapter that the dataset has changed
     */
    public void reloadTermsTab() {
        mTermsTab.NotifyAdapterDataSetChanged();
    }

    /**
     * @param term
     */
    public void showTerm(Term term) {
        selectTab(1);
        if(term == null) {
            mTermsTab.showTerms();
        } else {
            mTermsTab.showTerm(term);
        }
    }

    /**
     * Specifies the width of the layout
     * @param width
     */
    public void setLayoutWidth(int width) {
        if(mRootView != null) {
            mRootView.setLayoutParams(new ViewGroup.LayoutParams(mLayoutWidth, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            mLayoutWidth = width;
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Pair scrollPosition = mNotesTab.getScroll();
        outState.putInt(STATE_NOTES_SCROLL_X, (int)scrollPosition.first);
        outState.putInt(STATE_NOTES_SCROLL_Y, (int)scrollPosition.second);
        outState.putInt(STATE_SELECTED_TAB, mSelectedTab);
    }
}
