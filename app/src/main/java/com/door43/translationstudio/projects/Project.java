package com.door43.translationstudio.projects;

import android.content.SharedPreferences;
import android.net.Uri;

import com.door43.translationstudio.R;
import com.door43.translationstudio.git.Repo;
import com.door43.translationstudio.git.tasks.StopTaskException;
import com.door43.translationstudio.git.tasks.repo.CommitTask;
import com.door43.translationstudio.util.AppContext;
import com.door43.util.ListMap;
import com.door43.tools.reporting.Logger;
import com.door43.util.Manifest;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Projects encapsulate the source text for a specific translation effort regardless of language.
 * This source text is subdivided into Chapters and Frames.
 */
public class Project implements Model {
    private ListMap<Chapter> mChapters = new ListMap<>();
    private ListMap<SourceLanguage> mSourceLanguages = new ListMap<>();
    private ListMap<SourceLanguage> mSourceLanguageDrafts = new ListMap<>();
    private List<Language> mTargetLanguages = new ArrayList<Language>();
    private Map<String,Language> mTargetLanguageMap = new HashMap<String, Language>();
    private ListMap<Term> mTerms = new ListMap<>();
    private List<PseudoProject> mPseudoProjects = new ArrayList<PseudoProject>();
    private Map<String, PseudoProject> mSudoProjectMap = new HashMap<String, PseudoProject>();
    private ListMap<Translation> mTitleTranslations = new ListMap<Translation>();
    private ListMap<Translation> mDescriptionTranslations = new ListMap<Translation>();

    // TODO: the extension should be placed in the app settings
    public static final String PROJECT_EXTENSION = "tstudio";

    private String mDefaultTitle;
    private final String mSlug;
    private int mDateModified = 0;
    private String mDefaultDescription;
    private String mSelectedChapterId = null;
    private String mSelectedSourceLanguageId;
    private String mSelectedTargetLanguageId;
    public static final String GLOBAL_PROJECT_SLUG = "uw";
    private static final String TAG = "project";
    public static final String PREFERENCES_TAG = "com.door43.translationstudio.projects";
    private static final String TRANSLATION_READY_TAG = "READY";
    private boolean mHasNotes = false;
    private String mSortKey;
    private Uri mSourceLanguageCatalogUri;
    private int mSourceLanguagesDateModified = 0;
    private boolean mAutosave = true;

    /**
     * Creates a new project
     * @param slug the project slug
     * @param dateModified the date the project was last modified (usualy when pulled from the server)
     */
    public Project(String slug, int dateModified) {
        mSlug = slug;
        mDateModified = dateModified;
        init();
    }

    /**
     * Creates a new project
     * @param slug the project slug
     */
    public Project(String slug) {
        mSlug = slug;
        init();
    }

    /**
     * Initializes default settings in the project
     */
    private void init() {
        // load the selected language
        SharedPreferences settings = AppContext.context().getSharedPreferences(PREFERENCES_TAG, AppContext.context().MODE_PRIVATE);
        mSelectedSourceLanguageId = settings.getString("selected_source_language_"+mSlug, null);
        mSelectedTargetLanguageId = settings.getString("selected_target_language_"+mSlug, null);
    }

    /**
     * Create a new project
     * @deprecated
     * @param title The human readable title of the project.
     * @param slug The machine readable slug identifying the project.
     * @param description A short description of the project.
     */
    public Project(String title, String slug, String description) {
        mDefaultTitle = title;
        mSlug = slug;
        mDefaultDescription = description;
        init();
    }

    /**
     * Sets whether the project should automatically save changes. e.g. selected languages.
     * @param autosave
     */
    public void setAutosave(boolean autosave) {
        mAutosave = autosave;
    }

    /**
     * Sets the title of the project.
     * @param title
     */
    public void setDefaultTitle(String title) {
        mDefaultTitle = title;
    }

    /**
     * Sets the description of the project. This can only be set once
     * @param description
     */
    public void setDefaultDescription(String description) {
        mDefaultDescription = description;
    }

    /**
     * Adds a translation of the project title
     * @param title the title of the project
     * @param l the language the title is in
     */
    public void setTitle(String title, SourceLanguage l) {
        mTitleTranslations.replace(l.getId(), new Translation(l, title));
    }

    /**
     * Adds a translation of the project description
     * @param description the description of the project
     * @param l the language the description is in
     */
    public void setDescription(String description, SourceLanguage l) {
        mDescriptionTranslations.replace(l.getId(), new Translation(l, description));
    }

    /**
     * Sets whether the translation in the currently selected target langauge is ready for submission
     * @param isReady
     */
    public void setTranslationIsReady(boolean isReady){
        File file = new File(getRepositoryPath(), TRANSLATION_READY_TAG);
        if(isReady == true) {
            // place a file in the repo so the server knows it is ready
            try {
                file.createNewFile();
            } catch (IOException e) {
                Logger.e(this.getClass().getName(), "Failed to create translation ready file", e);
                return;
            }
        } else {
            // remove the ready tag
            file.delete();
        }
    }

    /**
     * Sets whether the translation in the target langauge is ready for submission
     * @param isReady
     */
    public void setTranslationIsReady(Language targetLanguage, boolean isReady){
        File file = new File(getRepositoryPath(getId(), targetLanguage.getId()), TRANSLATION_READY_TAG);
        if(isReady == true) {
            // place a file in the repo so the server knows it is ready
            try {
                file.createNewFile();
            } catch (IOException e) {
                Logger.e(this.getClass().getName(), "Failed to create translation ready file", e);
                return;
            }
        } else {
            // remove the ready tag
            file.delete();
        }
    }

    /**
     * Checks if the translation in the currently selected target language is ready for submission
     * @return
     */
    public boolean translationIsReady() {
        File file = new File(getRepositoryPath(), TRANSLATION_READY_TAG);
        return file.exists();
    }

    /**
     * Checks if the translation is ready for submission
     * @param targetLanguage
     * @return
     */
    public boolean translationIsReady(Language targetLanguage) {
        File file = new File(getRepositoryPath(getId(), targetLanguage.getId()), TRANSLATION_READY_TAG);
        return file.exists();
    }

    /**
     * Dumps all the frames and chapters from the project
     */
    public void flush() {
        mChapters = new ListMap<>();
        mTerms = new ListMap<>();
        mSelectedChapterId = null;
    }

    /**
     * Returns the project id a.k.a the project slug.
     * @return
     */
    @Override
    public String getId() {
        return mSlug;
    }

    /**
     * Returns the title of the project for the specified language
     * @param l
     * @return
     */
    public String getTitle(SourceLanguage l) {
        Translation t = null;
        if(l != null) {
            t = mTitleTranslations.get(l.getId());
        }
        if(t != null) {
            return t.getText();
        } else {
            return mDefaultTitle;
        }
    }

    /**
     * Returns the project title
     * @return
     */
    public String getTitle() {
        if(mTitleTranslations.get(mSelectedSourceLanguageId) != null) {
            return mTitleTranslations.get(mSelectedSourceLanguageId).getText();
        } else {
            return mDefaultTitle;
        }
    }

    /**
     * Returns the description of the project for the specified language
     * @param l
     * @return
     */
    public String getDescription(SourceLanguage l) {
        if(l != null && mDescriptionTranslations.get(l.getId()) != null) {
            return mDescriptionTranslations.get(l.getId()).getText();
        } else {
            return mDefaultDescription;
        }
    }

    /**
     * Returns a description of the project
     * @return
     */
    public String getDescription() {
        if(mDescriptionTranslations.get(mSelectedSourceLanguageId) != null) {
            return mDescriptionTranslations.get(mSelectedSourceLanguageId).getText();
        } else {
            return mDefaultDescription;
        }
    }

    @Override
    public String getSortKey() {
        if(mSortKey != null) {
            return mSortKey;
        } else {
            return "00";
        }
    }

    /**
     * Sets the key by which the project is sorted
     * @param key
     */
    public void setSortKey(String key) {
        mSortKey = key;
    }

    /**
     * Returns the number of chapters in this project
     * @return
     */
    public int numChapters() {
        return mChapters.size();
    }

    /**
     * Returns the number of languages in this project
     * @return
     */
    public int numSourceLanguages() {
        return mSourceLanguages.size();
    }

    /**
     * Returns a chapter by id
     * @param id the chapter id
     * @return null if the chapter does not exist
     */
    public Chapter getChapter(String id) {
        return mChapters.get(id);
    }

    /**
     * Returns a chapter by index
     * @param index the chapter index
     * @return null if the chapter does not exist
     */
    public Chapter getChapter(int index){
        if(index < mChapters.size() && index >= 0) {
            return mChapters.get(index);
        } else {
            return null;
        }
    }

    /**
     * Sets the currently selected chapter in the project by id
     * @param id the chapter id
     * @return true if the chapter exists
     */
    public boolean setSelectedChapter(String id) {
        Chapter c = getChapter(id);
        if(c != null) {
            mSelectedChapterId = c.getId();
            storeSelectedChapter(c.getId());
        }
        return c != null;
    }

    /**
     * Sets the currently selected chapter in the project by index
     * @param index the chapter index
     * @return true if the chapter exists
     */
    public boolean setSelectedChapter(int index) {
        Chapter c = getChapter(index);
        if(c != null) {
            mSelectedChapterId = c.getId();
            storeSelectedChapter(c.getId());
        }
        return c != null;
    }

    /**
     * stores the selected chapter in the preferences so we can load it the next time the app starts
     * @param id
     */
    private void storeSelectedChapter(String id) {
        SharedPreferences settings = AppContext.context().getSharedPreferences(PREFERENCES_TAG, AppContext.context().MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("selected_chapter_"+mSlug, id);
        editor.remove("selected_frame_"+mSlug); // the frame needs to reset each time so it doesn't propogate between chapters
        editor.apply();
    }

    /**
     * Returns the currently selected chapter in the project
     * @return
     */
    public Chapter getSelectedChapter() {
        if(mSelectedChapterId == null && AppContext.context().rememberLastPosition()) {
            SharedPreferences settings = AppContext.context().getSharedPreferences(PREFERENCES_TAG, AppContext.context().MODE_PRIVATE);
            mSelectedChapterId = settings.getString("selected_chapter_" + mSlug, null);
        }

        Chapter selectedChapter = getChapter(mSelectedChapterId);
        return selectedChapter;
    }

    /**
     * Adds a sudo project. These are used to help categorize projects
     * @param p
     */
    public void addSudoProject(PseudoProject p) {
        if(!mSudoProjectMap.containsKey(p.getId())) {
            mSudoProjectMap.put(p.getId(), p);
            mPseudoProjects.add(p);
        }
    }

    /**
     * Returns the number of sudo projects in this project
     * @return
     */
    public int numSudoProjects() {
        return mPseudoProjects.size();
    }

    /**
     * Returns an array of meta categories for this project
     * @return
     */
    public PseudoProject[] getPseudoProjects() {
        return mPseudoProjects.toArray(new PseudoProject[mPseudoProjects.size()]);
    }


    /**
    * Returns the id of a meta category b
    * @param index
    * @return
    */
    public PseudoProject getSudoProject(int index) {
        PseudoProject[] pseudoProjects = getPseudoProjects();
        if(index < pseudoProjects.length && index >= 0) {
            return pseudoProjects[index];
        } else {
            return null;
        }
    }

    /**
     * Adds a chapter to the project
     * @param c the chapter to add
     */
    public void addChapter(Chapter c) {
        if(mChapters.get(c.getId()) == null) {
            c.setProject(this);
            mChapters.add(c.getId(), c);
        }
    }

    /**
     * Adds a source language to the project
     * @param l the source language to add
     */
    public boolean addSourceLanguage(SourceLanguage l) {
        if(mSourceLanguages.get(l.getId()) == null) {
            l.setProject(this);
            mSourceLanguages.add(l.getId(), l);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Adds a source language draft to the project
     * @param l
     */
    public boolean addSourceLanguageDraft(SourceLanguage l) {
        if(mSourceLanguageDrafts.get(l.getId()) == null) {
            l.setProject(this);
            mSourceLanguageDrafts.add(l.getId(), l);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Adds a target language to the project.
     * This is just used when importing translations from device to device.
     * @param l the target language to add
     * @return
     */
    public boolean addTargetLanguage(Language l) {
        if(!mTargetLanguageMap.containsKey(l.getId())) {
            mTargetLanguageMap.put(l.getId(), l);
            mTargetLanguages.add(l);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns an array of target languages.
     * This is just used when importing translations from device to device.
     * @return
     */
    public Language[] getTargetLanguages() {
        return mTargetLanguages.toArray(new Language[mTargetLanguages.size()]);
    }

    /**
     * Adds a term to the project
     * @param term
     */
    public void addTerm(Term term) {
        if(mTerms.get(term.getName()) == null) {
            mTerms.add(term.getName(), term);

            // also add the aliases
            List<String> aliases = term.getAliases();
            for(String alias:aliases) {
                if(mTerms.get(alias) == null) {
                    mTerms.add(alias, term);
                }
            }
        }
    }

    /**
     * Returns the number of terms there are in the project
     * @return
     */
    public int numTerms() {
        return mTerms.size();
    }

    /**
     * Returns a term by index
     * @param index
     * @return
     */
    public Term getTerm(int index) {
        if(index < mTerms.size() && index >= 0) {
            return mTerms.get(index);
        } else {
            return null;
        }
    }

    /**
     * Looks up a key term by name.
     * @param name the case sensitive name of the term
     * @return
     */
    public Term getTerm(String name) {
        return mTerms.get(name);
    }

    /**
     * Returns a list of terms in the project
     * @return
     */
    public List<Term> getTerms() {
        return mTerms.getAll();
    }

    /**
     * Checks if the project is being translated in the given language.
     * If no targetlanguage is selected this will always return false, but isTranslatingGlobal may still return true.
     * @param projectId the project to check
     * @param languageId the target language to check
     * @return
     */
    public static boolean isTranslating(String projectId, String languageId) {
//        SharedPreferences settings = AppContext.context().getSharedPreferences(PREFERENCES_TAG, AppContext.context().MODE_PRIVATE);
//        String selectedTargetLanguage = settings.getString("selected_target_language_"+projectId, null);

//        if(selectedTargetLanguage != null) {
        File dir = new File(Project.getRepositoryPath(projectId, languageId));
        String[] files = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return !s.equals(".git") && new File(file, s).isDirectory();
            }
        });
        return files != null && files.length > 0;
//        } else {
//            return false;
//        }
    }

    /**
     * Checks if any translation notes are being translated in this project.
     * @param projectId
     * @param langaugeId
     * @return
     */
    public static boolean isTranslatingNotes(String projectId, String langaugeId) {
        File dir = new File(TranslationNote.getRepositoryPath(projectId, langaugeId));
        String[] files = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return !s.equals(".git") && new File(file, s).isDirectory();
            }
        });
        return files != null && files.length > 0;
    }

    /**
     * Checks if the translation notes are being translated
     * @return
     */
    @Override
    public boolean isTranslatingNotes() {
        if(hasSelectedTargetLanguage()) {
            return isTranslatingNotes(getId(), getSelectedTargetLanguage().getId());
        } else {
            return false;
        }
    }

    /**
     * Checks to see if this project is currently being translated in the selected target language.
     * If no target language is selected this will always return false, but isTranslatingGlobal may still return true.
     * @return
     */
    @Override
    public boolean isTranslating() {
        if(hasSelectedTargetLanguage()) {
            return isTranslating(getId(), getSelectedTargetLanguage().getId());
        } else {
            return false;
        }
    }

    @Override
    public String getType() {
        return "project";
        }

    /**
     * Checks if this project is the currently selected one
     * @return
     */
    @Override
    public boolean isSelected() {
        Project p = AppContext.projectManager().getSelectedProject();
        if(p == null) return false;
        return p.getId().equals(getId());
    }

    /**
     * Checks to see if this project is currently being translated in any language other than the currently selected one
     * @return
     */
    @Override
    public boolean isTranslatingGlobal() {
        File dir = new File(Project.getProjectsPath());
        String[] files = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File file, String name) {
                String[] pieces = name.split("-");
                // uw-[proj]-[lang]
                if (pieces.length == 3) {
                    // make sure the dir is not empty
                    String[] contents = new File(file, name).list(new FilenameFilter() {
                        @Override
                        public boolean accept(File file, String s) {
                            return !s.equals(".git") && new File(file, s).isDirectory();
                        }
                    });
                    if (contents != null && contents.length > 0) {
                        return pieces[0].equals(GLOBAL_PROJECT_SLUG) && pieces[1].equals(getId()) && !pieces[2].equals(mSelectedTargetLanguageId);
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        });
        return files != null && files.length > 0;
    }

    /**
     * Checks if the project translation notes are being translated in any language other than the currently selected one
     * @return
     */
    @Override
    public boolean isTranslatingNotesGlobal() {
        File dir = new File(Project.getProjectsPath());
        String[] files = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File file, String name) {
                String[] pieces = name.split("-");
                // uw-[proj]-[lang]-notes
                if(pieces.length == 4 && pieces[3].equals("notes")) {
                    // make sure the dir is not empty
                    String[] contents = new File(file, name).list(new FilenameFilter() {
                        @Override
                        public boolean accept(File file, String s) {
                            return !s.equals(".git") && new File(file, s).isDirectory();
                        }
                    });
                    if(contents != null && contents.length > 0) {
                        return pieces[0].equals(GLOBAL_PROJECT_SLUG) && pieces[1].equals(getId()) && !pieces[2].equals(mSelectedTargetLanguageId);
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        });
        return files != null && files.length > 0;
    }

    /**
     * Returns the currently selected source language
     * TODO: in some cases it would be nice not to have the language automatically selected. However, this would require some work to migrate all the code to support this.
     */
    @Override
    public SourceLanguage getSelectedSourceLanguage() {
        SourceLanguage selectedLanguage = getSourceLanguage(mSelectedSourceLanguageId);
        if(selectedLanguage == null) {
            // auto select the first language if no other chapter has been selected
            int defaultLanguageIndex = 0;
//            setSelectedSourceLanguage(defaultLanguageIndex);
            return getSourceLanguage(defaultLanguageIndex);
        } else {
            return selectedLanguage;
        }
    }

    /**
     * Sets the currently selected target language in the project by id
     * @param id the language id
     * @return
     */
    public boolean setSelectedTargetLanguage(String id) {
        Language l = AppContext.projectManager().getLanguage(id);
        if(l != null) {
            mSelectedTargetLanguageId = l.getId();
            storeSelectedTargetLanguage(mSelectedTargetLanguageId);
        }
        return l != null;
    }

    /**
     * Sets the currently selected target language in the project by index
     * @param index the language index
     * @return true if the language exists
     */
    public boolean setSelectedTargetLanguage(int index) {
        Language l = AppContext.projectManager().getLanguage(index);
        if(l != null) {
            mSelectedTargetLanguageId = l.getId();
            storeSelectedTargetLanguage(mSelectedTargetLanguageId);
        }
        return l != null;
    }

    /**
     * stores the selected target language in the preferences so we can load it the next time the app starts
     * @param slug
     */
    private void storeSelectedTargetLanguage(String slug) {
        SharedPreferences settings = AppContext.context().getSharedPreferences(PREFERENCES_TAG, AppContext.context().MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("selected_target_language_"+mSlug, slug);
        editor.apply();
    }

    /**
     * Returns the currently selected target language
     * If no target language has been chosen it will return the default language
     * @return
     */
    public Language getSelectedTargetLanguage() {
        Language selectedLanguage = AppContext.projectManager().getLanguage(mSelectedTargetLanguageId);
        if(selectedLanguage == null) {
            // auto select the first language
            int defaultLanguageIndex = 0;
            return AppContext.projectManager().getLanguage(defaultLanguageIndex);
        } else {
            return selectedLanguage;
        }
    }

    /**
     * Checks if the user has chosen a target language for this project yet.
     * @return
     */
    public boolean hasSelectedTargetLanguage() {
        return mSelectedTargetLanguageId != null;
    }

    /**
     * Sets the currently selected source language in the project by id
     * @param id the language id
     * @return true if the language exists
     */
    public boolean setSelectedSourceLanguage(String id) {
        Language l = getSourceLanguage(id);
        if(l != null) {
            mSelectedSourceLanguageId = l.getId();
            if(mAutosave) {
                storeSelectedSourceLanguage(mSelectedSourceLanguageId);
            }
        }
        return l != null;
    }

    /**
     * Sets the currently selected source language in the project by index
     * @param index the language index
     * @return true if the language exists
     */
    public boolean setSelectedSourceLanguage(int index) {
        Language l = getSourceLanguage(index);
        if(l != null) {
            mSelectedSourceLanguageId = l.getId();
            if(mAutosave) {
                storeSelectedSourceLanguage(mSelectedSourceLanguageId);
            }
        }
        return l != null;
    }

    /**
     * stores the selected target language in the preferences so we can load it the next time the app starts
     * @param slug
     */
    private void storeSelectedSourceLanguage(String slug) {
        SharedPreferences settings = AppContext.context().getSharedPreferences(PREFERENCES_TAG, AppContext.context().MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("selected_source_language_"+mSlug, slug);
        editor.apply();
    }

    /**
     * Returns the path to the image for this project
     * @return
     */
    public String getImagePath() {
        return "sourceTranslations/" + getId() + "/icon.jpg";
    }

    public String getDefaultImagePath() {
        return getImagePath();
    }

    /**
     * Finds a language by the language code
     * @param id the language code
     * @return null if the language does not exist
     */
    public SourceLanguage getSourceLanguage(String id) {
        return mSourceLanguages.get(id);
    }

    /**
     * Finds a language by index
     * @param index the language index
     * @return null if the language does not exist
     */
    public SourceLanguage getSourceLanguage(int index){
        if(index < mSourceLanguages.size() && index >= 0) {
            return mSourceLanguages.get(index);
        } else {
            return null;
        }
    }

    /**
     * Returns the absolute repository path for the given target language
     * @deprecated use ProjectManager.getRepositoryPath() instead
     * @param projectId
     * @param languageId
     * @return
     */
    public static String getRepositoryPath(String projectId, String languageId) {
        return AppContext.context().getFilesDir() + "/" + AppContext.context().getResources().getString(R.string.git_repository_dir) + "/" + GLOBAL_PROJECT_SLUG + "-" + projectId + "-" + languageId + "/";
    }

    /**
     * Returns the absolute path to the directory of projects. e.g. the git directory.
     * @return
     */
    public static String getProjectsPath() {
        return AppContext.context().getFilesDir() + "/" + AppContext.context().getResources().getString(R.string.git_repository_dir);
    }

    /**
     * Returns the absoute repository path for the currently selected language
     * @deprecated use ProjectManager.getRepositoryPath() instead
     * @return
     */
    public String getRepositoryPath() {
        return getRepositoryPath(getId(), getSelectedTargetLanguage().getId());
    }


    /**
     * Returns a list of source languages for this project
     * @return
     */
    public List<SourceLanguage> getSourceLanguages() {
        return mSourceLanguages.getAll();
    }

    /**
     * Returns the source language drafts for this project
     * @return
     */
    public List<SourceLanguage> getSourceLanguageDrafts() {
        return mSourceLanguageDrafts.getAll();
    }

    /**
     * Returns a source language draft
     * @param id the id of the source language draft
     * @return
     */
    public SourceLanguage getSourceLanguageDraft(String id) {
        return mSourceLanguageDrafts.get(id);
    }

    /**
     * Returns the latest git commit id for the project repo with the given target language
     * @param l the target language for the repo to check
     * @return
     */
    public String getLocalTranslationVersion(Language l) {
        Repo repo = new Repo(getRepositoryPath(getId(), l.getId()));
        try {
            Iterable<RevCommit> commits = repo.getGit().log().setMaxCount(1).call();
            RevCommit commit = null;
            for(RevCommit c : commits) {
                commit = c;
            }
            if(commit != null) {
                String[] pieces = commit.toString().split(" ");
                return pieces[1];
            } else {
                return null;
            }
        } catch (GitAPIException e) {
            Logger.e(this.getClass().getName(), "failed to fetch the git commit", e);
        } catch (StopTaskException e) {
            Logger.e(this.getClass().getName(), "the task was stopped", e);
        }
        return null;
    }

    /**
     * Returns the latest git commit id for the project repo with the selected target language
     * @return
     */
    public String getLocalTranslationVersion() {
        return getLocalTranslationVersion(getSelectedTargetLanguage());
    }

    /**
     * Adds and commits the changes to the repository
     */
    public void commit(final OnCommitComplete callback) {
        final Repo repo = new Repo(ProjectManager.getRepositoryPath(this, getSelectedTargetLanguage()));

        CommitTask commit = new CommitTask(repo, ".", new CommitTask.OnAddComplete() {
            @Override
            public void success() {
                if(callback != null) {
                    callback.success();
                }
            }

            @Override
            public void error(Throwable e) {
                Logger.e(this.getClass().getName(), "failed to commit the project changes", e);
                if(callback != null) {
                    callback.error(e);
                }
            }
        });
        commit.executeTask();
    }

    /**
     * Generates the remote path for a local repo
     * @param lang
     * @return
     */
//    public String getRemotePath(Language lang) {
//        String server = AppContext.context().getUserPreferences().getString(SettingsActivity.KEY_PREF_GIT_SERVER, AppContext.context().getResources().getString(R.string.pref_default_git_server));
//        return server + ":tS/" + AppContext.udid() + "/" + GLOBAL_PROJECT_SLUG + "-" + getId() + "-" + lang.getId();
//    }

//    /**
//     * Generates the remote path for a local repo from the currently selected target language
//     * @return
//     */
//    public String getRemotePath() {
//        return getRemotePath(getSelectedTargetLanguage());
//    }

    /**
     * Returns the chapters in this project
     * @return
     */
    public Chapter[] getChapters() {
        return mChapters.getAll().toArray(new Chapter[mChapters.size()]);
    }

    /**
     * Returns an array of target languages that are currently being translated
     * @return
     */
    public Language[] getActiveTargetLanguages() {
        File dir = new File(Project.getProjectsPath());
        // find active project dirs
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                String[] pieces = s.split("-");
                if (pieces.length == 3) {
                    // make sure dir is not empty
                    File[] contents = file.listFiles();
                    if (contents != null && contents.length > 0) {
                        // find project dirs
                        return pieces[0].equals(GLOBAL_PROJECT_SLUG) && pieces[1].equals(getId());
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        });
        // collect active languages
        List<Language> languages = new ArrayList<Language>();
        for(File f:files) {
            String[] pieces = f.getName().split("-");
            if(pieces.length == 3) {
                Language l = AppContext.projectManager().getLanguage(pieces[2]);
                if(l != null) {
                    languages.add(l);
                }
            }
        }
        return languages.toArray(new Language[languages.size()]);
    }

    /**
     * Returns the date the project was last modified
     * @return
     */
    public int getDateModified() {
        return mDateModified;
    }

    /**
     * Set the date the project was last modified
     * @param dateModified
     */
    public void setDateModified(int dateModified) {
        mDateModified = dateModified;
    }

    /**
     * Specifies whether or not the project has translation notes available for at least some of the frames
     * @param hasNotes
     */
    public void setHasNotes(boolean hasNotes) {
        mHasNotes = hasNotes;
    }

    /**
     * Checks if this project contains translation notes for at least some of it's frames
     * @return
     */
    public boolean hasNotes() {
        return mHasNotes;
    }

    /**
     * Checks if this project contains any key terms
     * @return
     */
    public boolean hasTerms() {
        return numTerms() > 0;
    }

    /**
     * Called when one of the chapters in this project has been saved.
     * TODO: we probably don't want to do this every time something is saved. Just once when the project language or resource is changed will be enough. However project don't know when the resource selection has changed
     * @param chapter
     */
    public void onChapterSaved(Chapter chapter) {
        Manifest m = getManifest(this, getSelectedTargetLanguage());

        m.put("slug", getId());

        // update the language info in the manifest
        JSONObject sourceLangJson = new JSONObject();
        JSONObject resourceJson = new JSONObject();
        try {
            resourceJson.put("slug", getSelectedSourceLanguage().getSelectedResource().getId());
            resourceJson.put("date_modified", getSelectedSourceLanguage().getSelectedResource().getDateModified());
            sourceLangJson.put("slug", getSelectedSourceLanguage().getId());
            sourceLangJson.put("date_modified", getSelectedSourceLanguage().getDateModified());
            sourceLangJson.put("resource", resourceJson);
            m.put("source_language", sourceLangJson);
        } catch (JSONException e) {
            Logger.e(this.getClass().getName(), "failed to update the manifest", e);
        }

        JSONObject targetLangJson = new JSONObject();
        try {
            targetLangJson.put("slug", getSelectedTargetLanguage().getId());
            targetLangJson.put("name", getSelectedTargetLanguage().getName());
            targetLangJson.put("direction", getSelectedTargetLanguage().getDirection().getLabel());
            m.put("target_language", targetLangJson);
        } catch (JSONException e) {
            Logger.e(this.getClass().getName(), "failed to update the manifest", e);
        }

        // invalidate checking questions
        m.remove("checking_questions");

        // add and commit changes
        Repo repo = new Repo(ProjectManager.getRepositoryPath(this, getSelectedTargetLanguage()));
        CommitTask add = new CommitTask(repo, ".", null);
        add.executeTask();
    }

    /**
     * Returns an instance of the project manifest
     * @param project
     * @param target
     * @return
     */
    public static Manifest getManifest(Project project, Language target) {
        return Manifest.generate(new File(ProjectManager.getRepositoryPath(project, target)));
    }

    /**
     * Checks if a source language has been selected
     * @return
     */
    public boolean hasSelectedSourceLanguage() {
        return getSourceLanguage(mSelectedSourceLanguageId) != null;
    }

    /**
     * Generates a project instance from json
     *
     * @param json
     * @return the project or null
     */
    public static Project generate(JSONObject json) {
        try {
            Project p = new Project(json.get("slug").toString(), Integer.parseInt(json.get("date_modified").toString()));

            // sorting key
            if (json.has("sort")) {
                p.setSortKey(json.getString("sort"));
            }

            // meta (Pseudo projects)
            if (json.has("meta")) {
                JSONArray jsonMeta = json.getJSONArray("meta");
                for (int j = 0; j < jsonMeta.length(); j++) {
                    p.addSudoProject(new PseudoProject(jsonMeta.get(j).toString()));
                }
            }

            // language catalog
            if (json.has("lang_catalog")) {
                p.setSourceLanguageCatalog(json.getString("lang_catalog"));
            }
            return p;
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Specifies the language catalog url for this project
     * This is where the source languages will be downloaded from
     * @param languageCatalogUrl
     */
    public void setSourceLanguageCatalog(String languageCatalogUrl) {
        if(languageCatalogUrl != null) {
            mSourceLanguageCatalogUri = Uri.parse(languageCatalogUrl);

            // set the source languges date modified
            Uri uri = Uri.parse(languageCatalogUrl);
            String dateModified = uri.getQueryParameter("date_modified");
            if(dateModified != null) {
                mSourceLanguagesDateModified = Integer.parseInt(dateModified);
            }
        } else {
            mSourceLanguageCatalogUri = null;
        }
    }

    /**
     * Returns the url to the language catalog
     * @return the url string or null
     */
    public Uri getSourceLanguageCatalog() {
        return mSourceLanguageCatalogUri;
    }

    /**
     * Returns the date the source language catalog was last modified
     * @return
     */
    public int getSourceLanguagesDateModified() {
        return mSourceLanguagesDateModified;
    }

    /**
     * Serializes the project info
     * This does not include any languages or chapters
     * @return
     */
    @Override
    public JSONObject serialize() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("slug", mSlug);
        json.put("lang_catalog", mSourceLanguageCatalogUri.toString());
        json.put("date_modified", mDateModified);
        json.put("sort", mSortKey);
        JSONArray meta = new JSONArray();
        for(PseudoProject sp:mPseudoProjects) {
            meta.put(sp.getId());
        }
        json.put("meta", meta);
        return json;
    }

    /**
     * Serializes the source language.
     * This will inject the project translation into the serialization
     * @param l
     * @return
     */
    public JSONObject serializeSourceLanguage(SourceLanguage l) throws JSONException {
        JSONObject json = l.serialize();
        JSONObject pJson = new JSONObject();
        pJson.put("desc", getDescription(l));
        pJson.put("name", getTitle(l));
        JSONArray meta = new JSONArray();
        for(PseudoProject sp:mPseudoProjects) {
            meta.put(sp.getTitle(l));
        }
        pJson.put("meta", meta);
        json.put("project", pJson);
        return json;
    }

    /**
     * This creates a new copy of this project without the chapters and terms.
     * @return
     */
    public Project softClone() {
        Project p = new Project(getId(), getDateModified());
        for(SourceLanguage l:mSourceLanguages.getAll()) {
            p.addSourceLanguage(l);
        }
        for(SourceLanguage l:mSourceLanguageDrafts.getAll()) {
            p.addSourceLanguageDraft(l);
        }
        for(Language l:mTargetLanguages) {
            p.addTargetLanguage(l);
        }
        for(PseudoProject sp:mPseudoProjects) {
            p.addSudoProject(sp);
        }
        for(Translation t:mTitleTranslations.getAll()) {
            p.setTitle(t.getText(), (SourceLanguage) t.getLanguage());
        }
        for(Translation t:mDescriptionTranslations.getAll()) {
            p.setDescription(t.getText(), (SourceLanguage) t.getLanguage());
        }
        p.setDefaultTitle(mDefaultTitle);
        p.setDefaultDescription(mDefaultDescription);
        p.setSortKey(mSortKey);
        p.setSourceLanguageCatalog(mSourceLanguageCatalogUri.toString());
        p.setAutosave(mAutosave);
        return p;
    }

    public interface OnCommitComplete {
        void success();
        void error(Throwable e);
    }

    /**
     * Checks if the project is indexed
     * @return
     */
    public boolean isIndexed() {
        File indexDir = new File(AppContext.context().getCacheDir(), "index");
        File projectDir = new File(indexDir, getId());
        File projectReadyFile = new File(projectDir, "ready.index");
        return projectReadyFile.exists();
    }
}
