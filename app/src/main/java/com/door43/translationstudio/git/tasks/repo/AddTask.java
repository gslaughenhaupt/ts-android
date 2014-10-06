package com.door43.translationstudio.git.tasks.repo;

import android.content.DialogInterface;

import com.door43.translationstudio.R;
import com.door43.translationstudio.git.tasks.StopTaskException;
import com.door43.translationstudio.git.Repo;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

public class AddTask extends RepoOpTask {

    private String mFilePattern;
    private OnAddComplete mCallback;

    public AddTask(Repo repo, String filepattern, OnAddComplete callback) {
        super(repo);
        mFilePattern = filepattern;
        mCallback = callback;
        // TODO: need to fire an optional callback
//        setSuccessMsg(R.string.success_auto_save);
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        return addToStage();
    }

    protected void onPostExecute(Boolean isSuccess) {
        super.onPostExecute(isSuccess);
        // fire the callback
        if(isSuccess) {
            mCallback.success();
        } else {
            mCallback.error();
        }
    }

    public boolean addToStage() {
        Git git = null;
        try {
            git = mRepo.getGit();
        } catch (StopTaskException e) {
            setException(e);
            return false;
        }

        // stage changes
        AddCommand add = git.add();
        try {
            add.addFilepattern(mFilePattern).call();
        } catch (Throwable e) {
            setException(e);
            return false;
        }

        // commit the change
        CommitCommand commit = git.commit();
        commit.setAll(true);
        commit.setMessage("auto save");
        try {
            commit.call();
        } catch (GitAPIException e) {
            setException(e);
            return false;
        }
        return true;
    }

    public interface OnAddComplete {
        public void success();
        public void error();
    }
}