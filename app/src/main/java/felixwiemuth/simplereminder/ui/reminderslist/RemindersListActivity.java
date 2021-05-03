/*
 * Copyright (C) 2018-2021 Felix Wiemuth and contributors (see CONTRIBUTORS.md)
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
 */

package felixwiemuth.simplereminder.ui.reminderslist;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import de.cketti.library.changelog.ChangeLog;
import felixwiemuth.simplereminder.Prefs;
import felixwiemuth.simplereminder.R;
import felixwiemuth.simplereminder.ui.AddReminderDialogActivity;
import felixwiemuth.simplereminder.ui.SettingsActivity;
import felixwiemuth.simplereminder.ui.actions.DisplayChangeLog;
import felixwiemuth.simplereminder.ui.util.HtmlDialogFragment;
import felixwiemuth.simplereminder.ui.util.UIUtils;
import felixwiemuth.simplereminder.util.ImplementationError;

public class RemindersListActivity extends AppCompatActivity {

    /**
     * The {@link PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager viewPager;
    private TabLayout tabLayout;
    private Toolbar toolbar;
    private RemindersListFragment remindersFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminders_list);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.container);

        tabLayout.setupWithViewPager(viewPager);

        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        viewPager.setAdapter(mSectionsPagerAdapter);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            if (!Prefs.isAddReminderDialogUsed(RemindersListActivity.this)) {
                Toast.makeText(RemindersListActivity.this, R.string.toast_info_add_reminder_dialog, Toast.LENGTH_LONG).show();
            }
            startActivityForResult(new Intent(this, AddReminderDialogActivity.class), 0);
        });

        showStartupDialogs();
    }

    /**
     * Show dialogs with messages or question to be shown to the user on startup.
     */
    private void showStartupDialogs() {
        // NOTE: the user will see the dialogs in reversed order as they are opened.

        // Changelog
        ChangeLog changeLog = new ChangeLog(this);
        if (changeLog.isFirstRun()) {
            changeLog.getLogDialog().show();
        }

        // Check whether battery optimization is disabled and show dialog to disable it otherwise.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkBatteryOptimization();
        }

        // Check whether run on boot is enabled and whether should ask user to enable it.
        checkRunOnBoot();

        // Show welcome dialog if version changed
        if (!Prefs.checkWelcomeMessageShown(this)) {
            UIUtils.showMessageDialog(R.string.dialog_welcome_title, getString(R.string.welcome_message), this);
        }

    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Intent content is not used
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (remindersFragment != null) {
                remindersFragment.reloadRemindersListAndUpdateRecyclerView();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_reminders_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_about) {
            try {
                PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                String title = getString(R.string.app_name) + " " + packageInfo.versionName;
                HtmlDialogFragment.displayHtmlDialogFragment(getSupportFragmentManager(), title, R.raw.about, DisplayChangeLog.class);
            } catch (PackageManager.NameNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void checkRunOnBoot() {
        if (Prefs.isRunOnBoot(this) || Prefs.isRunOnBootDontShowAgain(this)) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_startup_run_on_boot_title)
                .setMessage(R.string.dialog_startup_run_on_boot_message)
                .setPositiveButton(R.string.dialog_startup_run_on_boot_enable, (d, i) -> {
                    Prefs.enableRunOnBoot(this, this);
                })
                .setNeutralButton(R.string.dialog_startup_later, null)
                .setNegativeButton(R.string.dialog_startup_dont_show_again, (d, i) -> {
                    Prefs.setRunOnBootDontShowAgain(this);
                })
                .show();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkBatteryOptimization() {
        if (Prefs.isBatteryOptimizationDontShowAgain(this) || Prefs.isIgnoringBatteryOptimization(this)) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_startup_disable_battery_optimization_title)
                .setMessage(R.string.dialog_startup_disable_battery_optimization_message)
                .setPositiveButton(R.string.dialog_startup_disable_battery_optimization_turn_off,
                        (d, i) -> startActivity(Prefs.getIntentDisableBatteryOptimization(this)))
                .setNeutralButton(R.string.dialog_startup_later, null)
                .setNegativeButton(R.string.dialog_startup_dont_show_again,
                        (d, i) -> Prefs.setBatteryOptimizationDontShowAgain(this))
                .show();
    }


    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.tab_reminders);
                case 1:
                    return getString(R.string.tab_templates);
                default:
                    throw new ImplementationError("Invalid tab number " + position);
            }
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    remindersFragment = RemindersListFragment.newInstance();
                    return remindersFragment;
                case 1:
                    return TemplatesFragment.newInstance();
                default:
                    throw new ImplementationError("Invalid tab number " + position);

            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    }
}
