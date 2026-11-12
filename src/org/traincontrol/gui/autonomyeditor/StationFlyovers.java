package org.traincontrol.gui.autonomyeditor;

import java.util.List;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.util.I18n;

/**
 * What each (i) on Station Settings and its Train tab says (FR-111): what the setting means now and what changing it
 * does, from the value it has.  Dynamic information, so FR-168's off switch leaves these on.
 */
final class StationFlyovers
{
    private StationFlyovers()
    {
    }

    /** "Now" and "If you change it", as one tooltip. */
    static String nowThenChange(String now, String change)
    {
        return "<html><body style='width: 300px'><b>" + I18n.t("autosetup.ui.flyNow") + "</b> " + AutonomyEditorPanel.escape(now)
            + "<br><b>" + I18n.t("autosetup.ui.flyChange") + "</b> " + AutonomyEditorPanel.escape(change) + "</body></html>";
    }

    static String stops(boolean on)
    {
        return on ? nowThenChange(I18n.t("autosetup.ui.flyStopOnNow"), I18n.t("autosetup.ui.flyStopOnChange"))
            : nowThenChange(I18n.t("autosetup.ui.flyStopOffNow"), I18n.t("autosetup.ui.flyStopOffChange"));
    }

    static String through(StationBoxes boxes, boolean must)
    {
        if (!boxes.runsThrough)
        {
            return nowThenChange(I18n.t(boxes.stops ? "autosetup.ui.flyThroughOffStopsNow" : "autosetup.ui.flyThroughOffNow"),
                I18n.t("autosetup.ui.flyThroughOffChange"));
        }

        // On a station, unticking it makes every trip end here first (stop-only)
        return nowThenChange(I18n.t(must ? "autosetup.ui.flyThroughMustNow" : "autosetup.ui.flyThroughOnNow"),
            I18n.t(boxes.stops ? "autosetup.ui.flyThroughOnStopsChange" : "autosetup.ui.flyThroughOnChange"));
    }

    static String fullAutonomy(boolean on)
    {
        return on ? nowThenChange(I18n.t("autosetup.ui.flyAutoOnNow"), I18n.t("autosetup.ui.flyAutoOnChange"))
            : nowThenChange(I18n.t("autosetup.ui.flyAutoOffNow"), I18n.t("autosetup.ui.flyAutoOffChange"));
    }

    static String maxLength(int length)
    {
        return nowThenChange(length == 0 ? I18n.t("autosetup.ui.flyMaxAnyNow") : I18n.f("autosetup.ui.flyMaxNow", length),
            I18n.t("autosetup.ui.flyMaxChange"));
    }

    /** @param open the names of the sides trains may arrive by, of `all` */
    static String arrivals(List<String> open, List<String> all)
    {
        if (all.isEmpty()) return nowThenChange(I18n.t("autosetup.ui.flyArriveNoneNow"), I18n.t("autosetup.ui.flyArriveNoneChange"));

        if (all.size() < 2) return nowThenChange(I18n.t("autosetup.ui.flyArriveOneNow"), I18n.t("autosetup.ui.flyArriveOneChange"));

        if (open.size() == all.size())
        {
            return nowThenChange(I18n.f("autosetup.ui.flyArriveAllNow", String.join(", ", all)), I18n.t("autosetup.ui.flyArriveAllChange"));
        }

        java.util.List<String> shut = new java.util.ArrayList<>(all);

        shut.removeAll(open);

        return nowThenChange(I18n.f("autosetup.ui.flyArriveSomeNow", String.join(", ", open), String.join(", ", shut)),
            I18n.f("autosetup.ui.flyArriveSomeChange", String.join(", ", shut)));
    }

    static String exitGuard(String signals)
    {
        return signals == null ? nowThenChange(I18n.t("autosetup.ui.flyExitNoneNow"), I18n.t("autosetup.ui.flyExitNoneChange"))
            : nowThenChange(I18n.f("autosetup.ui.flyExitNow", signals), I18n.t("autosetup.ui.flyExitChange"));
    }

    static String entryGuard(String signals)
    {
        return signals == null ? nowThenChange(I18n.t("autosetup.ui.flyEntryNoneNow"), I18n.t("autosetup.ui.flyEntryNoneChange"))
            : nowThenChange(I18n.f("autosetup.ui.flyEntryNow", signals), I18n.t("autosetup.ui.flyEntryChange"));
    }

    /** @param direction what the dropdown says */
    static String depart(String direction)
    {
        return nowThenChange(I18n.f("autosetup.ui.flyDepartNow", direction), I18n.t("autosetup.ui.flyDepartChange"));
    }

    static String turning(boolean may, boolean must)
    {
        String never = I18n.t("autosetup.ui.flyTurnNever");
        String mayText = I18n.t("autosetup.ui.flyTurnMay");
        String mustText = I18n.t("autosetup.ui.flyTurnMust");

        if (must) return nowThenChange(mustText, never + "  " + mayText);

        return may ? nowThenChange(mayText, never + "  " + mustText) : nowThenChange(never, mayText + "  " + mustText);
    }

    static String priority(int priority)
    {
        return nowThenChange(priority == 0 ? I18n.t("autosetup.ui.flyPriorityNoneNow") : I18n.f("autosetup.ui.flyPriorityNow", priority),
            I18n.t("autosetup.ui.flyPriorityChange"));
    }

    static String speed(int percent)
    {
        return nowThenChange(I18n.f("autosetup.ui.flySpeedNow", percent), I18n.t("autosetup.ui.flySpeedChange"));
    }

    static String whichTrains(AutonomySession.WhichTrains which, int listed)
    {
        switch (which)
        {
            case BLACKLIST:
                return nowThenChange(I18n.f("autosetup.ui.flyBlackNow", listed), I18n.t("autosetup.ui.flyBlackChange"));

            case WHITELIST:
                return nowThenChange(I18n.f("autosetup.ui.flyWhiteNow", listed), I18n.t("autosetup.ui.flyWhiteChange"));

            default:
                return nowThenChange(I18n.t("autosetup.ui.flyAllNow"), I18n.t("autosetup.ui.flyAllChange"));
        }
    }

    static String unavailable(String names)
    {
        return nowThenChange(names == null ? I18n.t("autosetup.ui.flyBlockNoneNow") : I18n.f("autosetup.ui.flyBlockNow", names),
            I18n.t("autosetup.ui.flyBlockChange"));
    }

    static String active(String train, boolean on)
    {
        return on ? nowThenChange(I18n.f("autosetup.ui.flyActiveOnNow", train), I18n.t("autosetup.ui.flyActiveOnChange"))
            : nowThenChange(I18n.f("autosetup.ui.flyActiveOffNow", train), I18n.t("autosetup.ui.flyActiveOffChange"));
    }

    static String facing(String train)
    {
        return nowThenChange(I18n.f("autosetup.ui.flyFacingNow", train), I18n.t("autosetup.ui.flyFacingChange"));
    }

    static String arrivedFrom()
    {
        return nowThenChange(I18n.t("autosetup.ui.flyArrivedNow"), I18n.t("autosetup.ui.flyArrivedChange"));
    }

    static String tail()
    {
        return nowThenChange(I18n.t("autosetup.ui.flyTailNow"), I18n.t("autosetup.ui.flyTailChange"));
    }

    /** A train row's (i) where the row has no answer: what the row is, then why it is empty now (OB-429). */
    static String unanswered(String means, String now)
    {
        return "<html><body style='width: 300px'>" + AutonomyEditorPanel.escape(means) + "<br><b>"
            + I18n.t("autosetup.ui.flyNow") + "</b> " + AutonomyEditorPanel.escape(now) + "</body></html>";
    }

    /** The same where no train stands here. */
    static String noTrain(String meansKey)
    {
        return unanswered(I18n.t(meansKey), I18n.t("autosetup.ui.flyNoTrain"));
    }
}
