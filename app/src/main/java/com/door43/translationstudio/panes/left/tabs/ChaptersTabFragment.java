package com.door43.translationstudio.panes.left.tabs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.door43.translationstudio.MainActivity;
import com.door43.translationstudio.R;
import com.door43.translationstudio.dialogs.ModelItemAdapter;
import com.door43.translationstudio.projects.Model;
import com.door43.translationstudio.projects.Project;
import com.door43.translationstudio.projects.SourceLanguage;
import com.door43.translationstudio.projects.TranslationManager;
import com.door43.util.tasks.GenericTaskWatcher;
import com.door43.translationstudio.tasks.LoadFramesTask;
import com.door43.translationstudio.util.AppContext;
import com.door43.tools.reporting.Logger;
import com.door43.translationstudio.util.TabsFragmentAdapterNotification;
import com.door43.translationstudio.util.TranslatorBaseFragment;
import com.door43.util.tasks.ManagedTask;
import com.door43.util.tasks.TaskManager;

/**
 * Created by joel on 8/29/2014.
 */
public class ChaptersTabFragment extends TranslatorBaseFragment implements TabsFragmentAdapterNotification, GenericTaskWatcher.OnFinishedListener {
    private ModelItemAdapter mModelItemAdapter;
    private ListView mListView;
    private GenericTaskWatcher mTaskWatcher;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_pane_left_chapters, container, false);
        mListView = (ListView)view.findViewById(R.id.chapters_list_view);

        mTaskWatcher = new GenericTaskWatcher(getActivity(), R.string.loading);
        mTaskWatcher.setOnFinishedListener(this);

        // create adapter
        if(mModelItemAdapter == null) {
            if(AppContext.projectManager().getSelectedProject() == null) {
                mModelItemAdapter = new ModelItemAdapter(app(), new Model[]{}, "");
            } else {
                mModelItemAdapter = new ModelItemAdapter(app(), AppContext.projectManager().getSelectedProject().getChapters(), "");
            }
        }
        // connect adapter
        mListView.setAdapter(mModelItemAdapter);
        mListView.deferNotifyDataSetChanged();
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long lo) {
                TranslationManager.save();
                if(getActivity() != null) {
                    Project p = AppContext.projectManager().getSelectedProject();
                    SourceLanguage l = p.getSelectedSourceLanguage();
                    p.setSelectedChapter(i);
                    ((MainActivity)getActivity()).reload();

                    // load frames
                    if(p.getSelectedChapter().getFrames().length == 0) {
                        LoadFramesTask task = new LoadFramesTask(p, l, l.getSelectedResource(), p.getSelectedChapter());
                        mTaskWatcher.watch(task);
                        TaskManager.addTask(task, task.TASK_ID);
                    } else {
                        openFramesTab();
                    }
                } else{
                    Logger.e(this.getClass().getName(), "onItemClickListener the activity is null");
                }
            }
        });
        return view;
    }

    /**
     * reloads and opens the frames tab
     */
    private void openFramesTab() {
        ((MainActivity) getActivity()).reload();
        ((MainActivity) getActivity()).openFramesTab();
        NotifyAdapterDataSetChanged();
    }

    @Override
    public void NotifyAdapterDataSetChanged() {
        if(mModelItemAdapter != null && AppContext.projectManager().getSelectedProject() != null) {
            mModelItemAdapter.changeDataSet(AppContext.projectManager().getSelectedProject().getChapters());
        } else if(mModelItemAdapter != null)  {
            mModelItemAdapter.changeDataSet(new Model[]{});
        }
        if(mListView != null) {
            mListView.setSelectionAfterHeaderView();
        }
    }

    @Override
    public void onFinished(ManagedTask task) {
        mTaskWatcher.stop();
         if(task instanceof LoadFramesTask) {
             openFramesTab();
         }
    }

    public void onDestroy() {
        mTaskWatcher.stop();
        super.onDestroy();
    }
}
