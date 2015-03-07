package com.door43.translationstudio;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.door43.translationstudio.device2device.DeviceToDeviceActivity;
import com.door43.translationstudio.projects.Project;
import com.door43.translationstudio.projects.ProjectManager;
import com.door43.translationstudio.util.AppContext;
import com.door43.translationstudio.util.ThreadableUI;
import com.door43.translationstudio.util.ToolAdapter;
import com.door43.translationstudio.util.ToolItem;
import com.door43.translationstudio.util.TranslatorBaseActivity;
import com.door43.util.Logger;

import java.util.ArrayList;

public class GetMoreProjectsActivity extends TranslatorBaseActivity {

    private ArrayList<ToolItem> mGetProjectTools = new ArrayList<>();
    private ToolAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_more_projects);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ListView list = (ListView)findViewById(R.id.getProjectsListView);
        mAdapter = new ToolAdapter(mGetProjectTools, this);
        list.setAdapter(mAdapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                ToolItem tool = mAdapter.getItem(i);
                // execute the get/update project action
                if (tool.isEnabled()) {
                    tool.getAction().run();
                } else {
                    app().showToastMessage(tool.getDisabledNotice());
                }
            }
        });

        init();
    }

    private void init() {
        Boolean hasNetwork = AppContext.context().isNetworkAvailable();
        mGetProjectTools.add(new ToolItem("Browse available projects", "New projects will be downloaded from the server", R.drawable.ic_download, new ToolItem.ToolAction() {
            @Override
            public void run() {
                browseProjects();
            }
        }, hasNetwork, getResources().getString(R.string.internet_not_available)));
        mGetProjectTools.add(new ToolItem("Update projects", "Project updates will be downloaded from the server", R.drawable.ic_update, new ToolItem.ToolAction() {
            @Override
            public void run() {
                updateProjects();
            }
        }, hasNetwork, getResources().getString(R.string.internet_not_available)));
        mGetProjectTools.add(new ToolItem("Transfer from device", "Projects will be transferred over the network from a nearby device", R.drawable.ic_phone, new ToolItem.ToolAction() {
            @Override
            public void run() {
                Intent intent = new Intent(GetMoreProjectsActivity.this, DeviceToDeviceActivity.class);
                Bundle extras = new Bundle();
                extras.putBoolean("startAsServer", false);
                extras.putBoolean("browseSourceProjects", true);
                intent.putExtras(extras);
                startActivity(intent);
            }
        }, false, "Not implimented"));// hasNetwork, getResources().getString(R.string.internet_not_available)));
        mGetProjectTools.add(new ToolItem("Import from storage", "Projects will be imported from the external storage on this device", R.drawable.ic_folder, new ToolItem.ToolAction() {
            @Override
            public void run() {
                // TODO: This is the same as for the Sharing activity though we should package it all up into a single method call.
            }
        }, false, "Not implimented"));
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.menu_get_more_projects, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateProjects() {
        final ProgressDialog dialog = new ProgressDialog(this);
        // this is a little hack to share the title between callbacks
        final String[] newProjectDownloadTitle = new String[1];
        newProjectDownloadTitle[0] = "";
        dialog.setMessage(getResources().getString(R.string.downloading_updates));
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setIndeterminate(true);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setMax(AppContext.projectManager().numProjects());

        final Handler handle = new Handler(Looper.getMainLooper());

        // download thread
        final ThreadableUI thread = new ThreadableUI(this) {
            @Override
            public void onStop() {
                dialog.show();
                AppContext.context().showToastMessage(getResources().getString(R.string.download_canceled));
            }
            @Override
            public void run() {
                // check for updates to current projects
                int numProjects = AppContext.projectManager().numProjects();
                for (int i = 0; i < numProjects; i ++) {
                    if(isInterrupted()) break;
                    Project p = AppContext.projectManager().getProject(i);

                    // update progress
                    final String title = String.format(getResources().getString(R.string.downloading_project_updates), p.getId());
                    final int progress = i;
                    handle.post(new Runnable() {
                        @Override
                        public void run() {
                            dialog.setIndeterminate(false);
                            dialog.setProgress(progress);
                            dialog.setMessage(title);
                        }
                    });
                    AppContext.projectManager().downloadProjectUpdates(p, new ProjectManager.OnProgressCallback() {
                        @Override
                        public void onProgress(final double progress, final String message) {
                            handle.post(new Runnable() {
                                @Override
                                public void run() {
                                    dialog.setSecondaryProgress((int)Math.round(dialog.getMax()*progress));
                                    SpannableStringBuilder spannable = new SpannableStringBuilder(message);
                                    spannable.setSpan(new RelativeSizeSpan(0.8f), 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    spannable.setSpan(new ForegroundColorSpan(AppContext.context().getResources().getColor(R.color.medium_gray)), 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    dialog.setMessage(TextUtils.concat(title, "\n", spannable));
                                }
                            });
                        }

                        @Override
                        public void onSuccess() {

                        }
                    });
                }

                // reload the selected project source
                if( AppContext.projectManager().getSelectedProject() != null) {
                    handle.post(new Runnable() {
                        @Override
                        public void run() {
                            dialog.setProgress(dialog.getMax());
                            dialog.setSecondaryProgress(dialog.getMax());
                            dialog.setIndeterminate(true);
                            dialog.setMessage(getResources().getString(R.string.loading_project_chapters));
                            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                        }
                    });
                    AppContext.projectManager().fetchProjectSource(AppContext.projectManager().getSelectedProject());
                }
            }

            @Override
            public void onPostExecute() {
                dialog.dismiss();
                if(!isInterrupted()) {
                    app().showToastMessage(R.string.project_updates_downloaded);
                }
            }
        };

        // enable cancel
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                thread.stop();
            }
        });

        // download confirmation
        new AlertDialog.Builder(this)
                .setMessage(R.string.update_confirmation)
                .setCancelable(false)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Logger.i(this.getClass().getName(), "downloading updates");
                        dialog.show();
                        thread.start();
                    }
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void browseProjects() {
        final ProgressDialog dialog = new ProgressDialog(this);
        // this is a little hack to share the title between callbacks
        final String[] newProjectDownloadTitle = new String[1];
        newProjectDownloadTitle[0] = "";
        dialog.setMessage(getResources().getString(R.string.downloading_updates));
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setIndeterminate(true);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setMax(AppContext.projectManager().numProjects());

        final Handler handle = new Handler(Looper.getMainLooper());

        // download thread
        final ThreadableUI thread = new ThreadableUI(this) {
            @Override
            public void onStop() {
                dialog.show();
                AppContext.context().showToastMessage(getResources().getString(R.string.download_canceled));
            }
            @Override
            public void run() {
                // check for new projects to download
                final String downloadNewTitle = getResources().getString(R.string.checking_for_new_projects);
                if(!isInterrupted()) {
                    handle.post(new Runnable() {
                        @Override
                        public void run() {
                            dialog.setIndeterminate(true);
                            dialog.setMax(100);
                            dialog.setProgress(0);
                            dialog.setSecondaryProgress(0);
                            dialog.setMessage(downloadNewTitle);
                        }
                    });
                    AppContext.projectManager().downloadNewProjects(new ProjectManager.OnProgressCallback() {
                        @Override
                        public void onProgress(final double progress, final String message) {
                            handle.post(new Runnable() {
                                @Override
                                public void run() {
                                    dialog.setIndeterminate(false);
                                    dialog.setProgress((int)Math.round(100*progress));
                                    dialog.setMessage(message);
                                    newProjectDownloadTitle[0] = message;
                                }
                            });
                        }

                        @Override
                        public void onSuccess() {

                        }
                    }, new ProjectManager.OnProgressCallback() {
                        @Override
                        public void onProgress(final double progress, final String message) {
                            handle.post(new Runnable() {
                                @Override
                                public void run() {
                                    dialog.setIndeterminate(false);
                                    dialog.setSecondaryProgress((int) Math.round(100 * progress));
                                    SpannableStringBuilder spannable = new SpannableStringBuilder(message);
                                    spannable.setSpan(new RelativeSizeSpan(0.8f), 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    spannable.setSpan(new ForegroundColorSpan(AppContext.context().getResources().getColor(R.color.medium_gray)), 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    dialog.setMessage(TextUtils.concat(newProjectDownloadTitle[0],"\n",spannable));
                                }
                            });
                        }

                        @Override
                        public void onSuccess() {

                        }
                    });
                }

                // reload the selected project source
                if( AppContext.projectManager().getSelectedProject() != null) {
                    handle.post(new Runnable() {
                        @Override
                        public void run() {
                            dialog.setProgress(dialog.getMax());
                            dialog.setSecondaryProgress(dialog.getMax());
                            dialog.setIndeterminate(true);
                            dialog.setMessage(getResources().getString(R.string.loading_project_chapters));
                            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                        }
                    });
                    AppContext.projectManager().fetchProjectSource(AppContext.projectManager().getSelectedProject());
                }
            }

            @Override
            public void onPostExecute() {
                dialog.dismiss();
                if(!isInterrupted()) {
                    app().showToastMessage(R.string.project_updates_downloaded);
                }

                // re-enable screen rotation
//                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            }
        };

        // enable cancel
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                thread.stop();
            }
        });

        // download confirmation
        new AlertDialog.Builder(this)
                .setMessage(R.string.update_confirmation)
                .setCancelable(false)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Logger.i(this.getClass().getName(), "downloading updates");
                        dialog.show();
                        thread.start();
                    }
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }
}
