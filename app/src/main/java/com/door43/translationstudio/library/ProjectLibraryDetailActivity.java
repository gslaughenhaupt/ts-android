package com.door43.translationstudio.library;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.view.MenuItem;

import com.door43.translationstudio.R;
import com.door43.translationstudio.util.TranslatorBaseActivity;

/**
 * An activity representing a single Project detail screen. This
 * activity is only used on handset devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a {@link ProjectLibraryListActivity}.
 * <p/>
 * This activity is mostly just a 'shell' activity containing nothing
 * more than a {@link ProjectLibraryDetailFragment}.
 */
public class ProjectLibraryDetailActivity extends TranslatorBaseActivity implements TranslationDraftsTabFragment.Callbacks, LibraryCallbacks {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_library_detail);

        // Show the Up button in the action bar.
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // savedInstanceState is non-null when there is fragment state
        // saved from previous configurations of this activity
        // (e.g. when rotating the screen from portrait to landscape).
        // In this case, the fragment will automatically be re-added
        // to its container so we don't need to manually add it.
        // For more information, see the Fragments API guide at:
        //
        // http://developer.android.com/guide/components/fragments.html
        //
        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            Bundle arguments = new Bundle();
            if(getIntent().getExtras() != null) {
                arguments = getIntent().getExtras();
            }
            arguments.putString(ProjectLibraryDetailFragment.ARG_ITEM_ID,
                    getIntent().getStringExtra(ProjectLibraryDetailFragment.ARG_ITEM_ID));
            ProjectLibraryDetailFragment fragment = new ProjectLibraryDetailFragment();
            fragment.setArguments(arguments);
            getFragmentManager().beginTransaction().add(R.id.project_detail_container, fragment).commit();
        }


    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown. Use NavUtils to allow users
            // to navigate up one level in the application structure. For
            // more details, see the Navigation pattern on Android Design:
            //
            // http://developer.android.com/design/patterns/navigation.html#up-vs-back
            //
            Intent intent = new Intent(this, ProjectLibraryListActivity.class);
            if(getIntent().getExtras() != null) {
                intent.putExtras(getIntent().getExtras());
            }
            NavUtils.navigateUpTo(this, intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onEmptyDraftsList() {
        ProjectLibraryDetailFragment fragment = (ProjectLibraryDetailFragment)getFragmentManager().findFragmentById(R.id.project_detail_container);
        fragment.hideDraftsTab();
    }

    @Override
    public void refreshUI() {
        // NOTE: right now this is only called when a project has been deleted
//        NavUtils.navigateUpFromSameTask(this);
        finish();
    }
}
