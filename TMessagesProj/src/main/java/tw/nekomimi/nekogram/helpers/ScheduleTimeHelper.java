package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberPicker;
import org.telegram.ui.Components.SeekBarView;

import java.util.Calendar;

import xyz.nextalone.nagram.NaConfig;

public final class ScheduleTimeHelper {

    private static final int DEFAULT_SCHEDULE_STEP_COUNT = 30;
    private static final int DEFAULT_SCHEDULE_LAST_MINUTE_STEP = 11;
    private static final int DEFAULT_SCHEDULE_LAST_HOUR_STEP = 22;
    private static final long SEND_WHEN_ONLINE_DATE = 0x7FFFFFFEL;

    private ScheduleTimeHelper() {
    }

    public static boolean shouldUseDefaultSchedule(long currentDate) {
        return currentDate <= 0 || currentDate == SEND_WHEN_ONLINE_DATE;
    }

    public static long getInitialTargetTime(long currentDate) {
        if (shouldUseDefaultSchedule(currentDate)) {
            int step = getDefaultScheduleStep(NaConfig.INSTANCE.getDefaultScheduledTime().Int());
            int minutes = getDefaultScheduleMinutes(step);
            return getTargetTimeFromNow(minutes);
        }
        return currentDate * 1000L;
    }

    public static void setPickersFromTargetTime(long targetTime, Calendar calendar, NumberPicker dayPicker, NumberPicker hourPicker, NumberPicker minutePicker) {
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        int days = (int) ((targetTime - calendar.getTimeInMillis()) / (24 * 60 * 60 * 1000));
        if (days >= 0) {
            calendar.setTimeInMillis(targetTime);
            minutePicker.setValue(calendar.get(Calendar.MINUTE));
            hourPicker.setValue(calendar.get(Calendar.HOUR_OF_DAY));
            dayPicker.setValue(days);
        }
    }

    public static void addDefaultScheduleSlider(
            Context context,
            LinearLayout container,
            Theme.ResourcesProvider resourcesProvider,
            Calendar calendar,
            NumberPicker dayPicker,
            NumberPicker hourPicker,
            NumberPicker minutePicker,
            Runnable onPickersChanged
    ) {
        final LinearLayout quickScheduleLayout = new LinearLayout(context);
        quickScheduleLayout.setOrientation(LinearLayout.VERTICAL);
        container.addView(quickScheduleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0, 8));

        final LinearLayout quickScheduleHeader = new LinearLayout(context);
        quickScheduleHeader.setOrientation(LinearLayout.HORIZONTAL);
        quickScheduleHeader.setGravity(Gravity.CENTER_VERTICAL);
        quickScheduleLayout.addView(quickScheduleHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 24, 22, 0, 22, 0));

        final int accentColor = Theme.getColor(Theme.key_player_progress, resourcesProvider);

        final TextView quickScheduleTitle = new TextView(context);
        quickScheduleTitle.setText(getString(R.string.DefaultScheduleDelay));
        quickScheduleTitle.setTextColor(accentColor);
        quickScheduleTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        quickScheduleTitle.setTypeface(AndroidUtilities.bold());
        quickScheduleTitle.setGravity(Gravity.CENTER_VERTICAL);
        quickScheduleTitle.setSingleLine(true);
        quickScheduleTitle.setEllipsize(TextUtils.TruncateAt.END);
        quickScheduleHeader.addView(quickScheduleTitle, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));

        final TextView quickScheduleValue = new TextView(context);
        int step = getDefaultScheduleStep(NaConfig.INSTANCE.getDefaultScheduledTime().Int());
        int minutes = getDefaultScheduleMinutes(step);
        quickScheduleValue.setText(formatDefaultScheduleMinutes(minutes));
        quickScheduleValue.setTextColor(accentColor);
        quickScheduleValue.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        quickScheduleValue.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        quickScheduleHeader.addView(quickScheduleValue, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        final SeekBarView quickScheduleSeekBar = new SeekBarView(context, resourcesProvider);
        quickScheduleSeekBar.setReportChanges(true);
        quickScheduleSeekBar.setSeparatorsCount(DEFAULT_SCHEDULE_STEP_COUNT);
        quickScheduleSeekBar.setProgress(getDefaultScheduleProgress(step));
        quickScheduleSeekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                int step = Math.round(progress * (DEFAULT_SCHEDULE_STEP_COUNT - 1));
                int minutes = getDefaultScheduleMinutes(step);
                quickScheduleValue.setText(formatDefaultScheduleMinutes(minutes));
                if (NaConfig.INSTANCE.getDefaultScheduledTime().Int() != minutes) {
                    NaConfig.INSTANCE.getDefaultScheduledTime().setConfigInt(minutes);
                }
                setPickersFromTargetTime(getTargetTimeFromNow(minutes), calendar, dayPicker, hourPicker, minutePicker);
                onPickersChanged.run();
                if (stop) {
                    quickScheduleSeekBar.setProgress(getDefaultScheduleProgress(step), true);
                }
            }

            @Override
            public CharSequence getContentDescription() {
                return quickScheduleTitle.getText() + ", " + quickScheduleValue.getText();
            }

            @Override
            public int getStepsCount() {
                return DEFAULT_SCHEDULE_STEP_COUNT - 1;
            }
        });
        quickScheduleLayout.addView(quickScheduleSeekBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 38, 13, 0, 13, 0));
    }

    private static int getDefaultScheduleMinutes(int step) {
        step = Utilities.clamp(step, DEFAULT_SCHEDULE_STEP_COUNT - 1, 0);
        if (step <= DEFAULT_SCHEDULE_LAST_MINUTE_STEP) {
            return (step + 1) * 5;
        } else if (step <= DEFAULT_SCHEDULE_LAST_HOUR_STEP) {
            return (step - 10) * 60;
        }
        return (step - DEFAULT_SCHEDULE_LAST_HOUR_STEP) * 24 * 60;
    }

    private static int getDefaultScheduleStep(int minutes) {
        int bestStep = 0;
        int bestDiff = Integer.MAX_VALUE;
        for (int i = 0; i < DEFAULT_SCHEDULE_STEP_COUNT; i++) {
            int diff = Math.abs(getDefaultScheduleMinutes(i) - minutes);
            if (diff < bestDiff) {
                bestStep = i;
                bestDiff = diff;
            }
        }
        return bestStep;
    }

    private static float getDefaultScheduleProgress(int step) {
        return step / (float) (DEFAULT_SCHEDULE_STEP_COUNT - 1);
    }

    private static String formatDefaultScheduleMinutes(int minutes) {
        if (minutes < 60 || minutes % 60 != 0) {
            return LocaleController.formatPluralString("Minutes", minutes);
        } else if (minutes < 24 * 60 || minutes % (24 * 60) != 0) {
            return LocaleController.formatPluralString("Hours", minutes / 60);
        }
        return LocaleController.formatPluralString("Days", minutes / (24 * 60));
    }

    private static long getTargetTimeFromNow(int minutes) {
        return roundUpToScheduleMinute(System.currentTimeMillis() + (long) minutes * 60 * 1000L);
    }

    private static long roundUpToScheduleMinute(long time) {
        return ((time + 59999L) / 60000L) * 60000L;
    }
}
