/*
 * Copyright (C) 2018-2023 Felix Wiemuth and contributors (see CONTRIBUTORS.md)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This file incorporates work covered by different licenses.
 * For further information see LICENSE.md.
 */

package felixwiemuth.simplereminder.ui.util;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import felixwiemuth.simplereminder.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//If you don't support Android 2.x, you should use the non-support version!

/**
 * Created by Adam Speakman on 24/09/13. http://speakman.net.nz
 * <p>
 * Edited by Felix Wiemuth 12/2016, 07/2017.
 */

/**
 * Displays an HTML document in a dialog fragment with the possibility to add special action links
 * that can trigger execution of app code ({@see Action}).
 */
public class HtmlDialogFragment extends DialogFragment {

    /**
     * An action to be performed when a "action:///action-name/arg1/arg2/..." link is clicked.
     */
    public interface Action {

        /**
         * Get the name of the action to be used in the URI.
         *
         * @return
         */
        String getName();

        /**
         * Perform an action.
         *
         * @param args    the path segments after the action name (arg1, arg2, ...), i.e.,
         *                everything between the separators "/" which themselves are not included,
         *                contains only non-empty arguments
         * @param context the current context of the WebView
         */
        void run(List<String> args, Context context);
    }

    private AsyncTask<Void, Void, String> loader;

    private Map<String, Action> actions = new HashMap<>();

    private static final String FRAGMENT_TAG = "felixwiemuth.simplereminder.HtmlDialogFragment";
    private static final String ARG_TITLE = "felixwiemuth.simplereminder.HtmlDialogFragment.ARG_TITLE";
    private static final String ARG_RES_HTML_FILE = "felixwiemuth.simplereminder.HtmlDialogFragment.ARG_RES_HTML_FILE";
    private static final String ARG_ACTIONS = "felixwiemuth.simplereminder.HtmlDialogFragment.ARG_ACTIONS";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //noinspection ConstantConditions
        for (String actionName : getArguments().getStringArray(ARG_ACTIONS)) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends Action> actionClass = (Class<? extends Action>) Class.forName(actionName);
                Action action = actionClass.newInstance();
                actions.put(action.getName(), action);
            } catch (java.lang.InstantiationException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Builds and displays a HTML dialog fragment.
     *
     * @param fm          a fragment manager instance used to display this HtmlDialogFragment
     * @param resTitle    the title for the dialog, as string resource
     * @param resHtmlFile the resource of the HTML file to display
     * @param actions     {@link Action}s that should be registered with the WebView to be shown
     */
    @SafeVarargs
    public static void displayHtmlDialogFragment(FragmentManager fm, @StringRes int resTitle, @RawRes int resHtmlFile, Class<? extends Action>... actions) {
        Bundle arguments = new Bundle();
        arguments.putInt(ARG_TITLE, resTitle);
        arguments.putInt(ARG_RES_HTML_FILE, resHtmlFile);
        addActionsToBundle(arguments, actions);
        constructFragment(arguments).displayFragment(fm);
    }

    /**
     * Builds and displays a HTML dialog fragment.
     *
     * @param fm          a fragment manager instance used to display this HtmlDialogFragment
     * @param title       the title for the dialog, as string
     * @param resHtmlFile the resource of the HTML file to display
     * @param actions     {@link Action}s that should be registered with the WebView to be shown
     */
    @SafeVarargs
    public static void displayHtmlDialogFragment(FragmentManager fm, String title, @RawRes int resHtmlFile, Class<? extends Action>... actions) {
        Bundle arguments = new Bundle();
        arguments.putString(ARG_TITLE, title);
        arguments.putInt(ARG_RES_HTML_FILE, resHtmlFile);
        addActionsToBundle(arguments, actions);
        constructFragment(arguments).displayFragment(fm);
    }

    private static void addActionsToBundle(Bundle bundle, Class<? extends Action>[] actions) {
        String[] actionNames = new String[actions.length];
        for (int i = 0; i < actionNames.length; i++) {
            actionNames[i] = actions[i].getCanonicalName();
        }
        bundle.putStringArray(ARG_ACTIONS, actionNames);
    }

    /**
     * @param arguments must include ARG_ACTIONS added by {@link #addActionsToBundle(Bundle,
     *                  Class[])}
     * @return
     */
    private static HtmlDialogFragment constructFragment(Bundle arguments) {
        // Create and show the dialog.
        HtmlDialogFragment newFragment = new HtmlDialogFragment();
        newFragment.setArguments(arguments);
        return newFragment;
    }

    private void displayFragment(FragmentManager fm) {
        FragmentTransaction ft = fm.beginTransaction();
        Fragment prev = fm.findFragmentByTag(FRAGMENT_TAG);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        show(ft, FRAGMENT_TAG);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        loadPage();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (loader != null) {
            loader.cancel(true);
        }
    }

    private WebView webView;
    private ProgressBar indeterminateProgress;

    @SuppressLint("StaticFieldLeak")
    private void loadPage() {
        // Load asynchronously in case of a very large file. The (implicit) reference to the outer class (fragment) in AsyncTask is not a problem as the fragment should stay alive while loading.
        loader = new AsyncTask<Void, Void, String>() {

            @Override
            protected String doInBackground(Void... params) {
                InputStream rawResource = getActivity().getResources().openRawResource(getArguments().getInt(ARG_RES_HTML_FILE)); //TODO error handling
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(rawResource));

                String line;
                StringBuilder sb = new StringBuilder();

                try {
                    while ((line = bufferedReader.readLine()) != null) {
                        sb.append(line);
                        sb.append("\n");
                    }
                    bufferedReader.close();
                } catch (IOException e) {
                    // TODO You may want to include some logging here.
                }

                return sb.toString();
            }

            @Override
            protected void onPostExecute(String body) {
                super.onPostExecute(body);
                if (getActivity() == null || isCancelled()) {
                    return;
                }
                indeterminateProgress.setVisibility(View.INVISIBLE);
                webView.setVisibility(View.VISIBLE);
                webView.loadDataWithBaseURL(null, body, "text/html", "utf-8", null);
                loader = null;
            }

        }.execute();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View content = LayoutInflater.from(getActivity()).inflate(R.layout.html_dialog_fragment, null);
        webView = (WebView) content.findViewById(R.id.html_dialog_fragment_web_view);
        // Set the WebViewClient (in API <24 have to parse URI manually)
        if (Build.VERSION.SDK_INT >= 24) {
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest webResourceRequest) {
                    Uri uri = webResourceRequest.getUrl(); // @TargetApi(Build.VERSION_CODES.N_MR1)
                    return HtmlDialogFragment.this.loadUrl(webView, uri);
                }
            });
        } else { //TODO test on an API < 24 device
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView webView, String url) {
                    Uri uri = Uri.parse(url);
                    return HtmlDialogFragment.this.loadUrl(webView, uri);
                }
            });
        }

        indeterminateProgress = (ProgressBar) content.findViewById(R.id.html_dialog_fragment_indeterminate_progress);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        Bundle arguments = getArguments();
        // if argument for title is given (string or int referencing a string resource) set the title
        if (arguments.getString(ARG_TITLE) != null) {
            builder.setTitle(arguments.getString(ARG_TITLE));
        } else {
            builder.setTitle(getArguments().getInt(ARG_TITLE)); //TODO error handling
        }
        builder.setView(content);
        return builder.create();
    }

    private boolean loadUrl(WebView webView, Uri uri) {
        if (uri.getScheme().equals("file")) {
            webView.loadUrl(uri.toString());
        } else if (uri.getScheme().equals("action")) {
            List<String> segments = uri.getPathSegments();
            if (segments.isEmpty()) {
                throw new RuntimeException("Error in WebView: No action name provided.");
            } else {
                handleAction(segments.get(0), segments.subList(1, segments.size()));
            }
        } else {
            // If the URI is not pointing to a local file, open with an ACTION_VIEW Intent
            webView.getContext().startActivity(new Intent(Intent.ACTION_VIEW, uri));
        }
        return true; // in both cases we handle the link manually
    }

    private void handleAction(String action, List<String> args) {
        Action a = actions.get(action);
        if (a == null) {
            throw new RuntimeException("Error in WebView: no action \"" + action + "\" registered.");
        } else {
            a.run(args, getContext());
        }
    }
}
