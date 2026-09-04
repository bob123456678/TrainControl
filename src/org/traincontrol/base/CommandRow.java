package org.traincontrol.base;

import java.util.ArrayList;
import org.traincontrol.util.I18n;
import java.util.List;

/**
 * A route command as three things a person picks, rather than a line they have to type.
 *
 * The editable shape of every command TrainControl understands turns out to be the same three columns:
 * what KIND of thing this is, WHICH one, and what to do to it. An accessory is "Accessory / 12 /
 * turn"; a locomotive speed is "Speed / BR 628 / 40"; a stop is "Stop" and nothing else. Three columns
 * of dropdowns cover all of it, which is what lets the editor be a table instead of a text box with
 * syntax rules.
 *
 * The point of this class is that the conversion can be tested. A route edited through controls is
 * only safe if a command taken apart into columns and put back together is the command it started as -
 * otherwise opening a route and pressing Save would quietly change it.
 *
 * A kind this does not know is not lost. {@link #of} answers null, and the editor keeps that command
 * as it found it - the same rule the condition rows follow for a bracket.
 */
public final class CommandRow
{
    /**
     * What a command does, as the editor offers it.
     *
     * Deliberately not RouteCommand.commandType: that enum carries kinds the editor has no controls
     * for, and mapping them here rather than exposing them keeps "what can be edited" in one place.
     */
    public enum Kind
    {
        ACCESSORY,

        // A signal, which is the same device as a switch at the same address with the same two
        // states - accessoryType only decides which picture gets drawn.  Two kinds anyway, because
        // "Signal 100 red" and "Switch 100 turn" are the same command said in the two vocabularies
        // real people use, and being offered the wrong pair of words is being asked to translate
        // your own layout.
        //
        // They BUILD identically, so nothing downstream has to know about the split; of() answers
        // ACCESSORY for both, since a stored command carries no record of which it was, and the
        // editor upgrades a row to SIGNAL when the layout says there is a signal at that address.
        SIGNAL,

        // A three-way point: two motors at consecutive addresses, which a route holds as two
        // ordinary accessory commands in a particular order with a pause between them.  One row
        // rather than two because setting one by hand means knowing which motor moves first and why,
        // and getting it wrong drives the point through a position nobody asked for.  ThreeWaySwitch
        // is where the three shapes are written down; this is only the row that stands for one.
        THREE_WAY,

        FEEDBACK,
        FUNCTION,
        LOCOMOTIVE_SPEED,
        LOCOMOTIVE_DIRECTION,
        STOP,
        FUNCTIONS_OFF,

        // The rest of what a route can do.  These were all readable and none of them buildable: a
        // route that used one could be opened, and the row was kept greyed and untouched, but there
        // was no way to add one.  AUTO_LOCOMOTIVE is the condition ConditionRows' own header uses as
        // its example, so until now the editor could not build the row its documentation illustrates.
        LIGHTS_ON,
        AUTONOMY_LIGHTS_ON,
        ROUTE,
        AUTO_LOCOMOTIVE
    }

    private final Kind kind;

    /** The accessory address, or the locomotive name.  Empty where the kind has no target. */
    private final String target;

    /** On/off, a speed, a function number, or a direction.  Empty where the kind has no setting. */
    private final String setting;

    /**
     * The decoder type of an accessory, or null for every other kind.
     *
     * Carried rather than assumed because MM2 and DCC are SEPARATE address spaces - MarklinAccessory
     * puts them at different UIDs - so a DCC turnout saved as MM2 does not fail to move, it moves a
     * different piece of railway, or invents a phantom one.  The editor had no column for this and
     * defaulted every accessory to MM2, which rewrote DCC routes on Save.
     */
    private final Accessory.accessoryDecoderType protocol;

    /**
     * How long to wait after this command, in milliseconds.  Zero means no wait.
     *
     * A layout timed so a slow point motor settles before the next command, or so two motors never
     * draw at once, is held together by these numbers and by nothing else on screen.
     */
    private final int delay;

    public CommandRow(Kind kind, String target, String setting)
    {
        this(kind, target, setting, null, 0);
    }

    public CommandRow(Kind kind, String target, String setting,
        Accessory.accessoryDecoderType protocol, int delay)
    {
        this.kind = kind;
        this.target = target == null ? "" : target;
        this.setting = setting == null ? "" : setting;
        this.protocol = protocol;
        this.delay = delay < 0 ? 0 : delay;
    }

    public Kind getKind()
    {
        return kind;
    }

    public String getTarget()
    {
        return target;
    }

    public String getSetting()
    {
        return setting;
    }

    /**
     * The accessory's decoder type, or null where the kind has none.
     */
    public Accessory.accessoryDecoderType getProtocol()
    {
        return protocol;
    }

    public int getDelay()
    {
        return delay;
    }

    /**
     * What a kind is called in the interface.
     *
     * The enum names are how the code spells these - LOCOMOTIVE_DIRECTION, AUTONOMY_LIGHTS_ON - and
     * they were being shown to the user as they are. A route editor is used by somebody who knows
     * their railway and has no reason to know how a Java enum is written.
     *
     * @param kind the kind
     * @return its name, in the user's language
     */
    public static String labelFor(Kind kind)
    {
        if (kind == null) return "";

        return I18n.t("route.kind." + kind.name());
    }

    /**
     * The kind a label names, or null when nothing matches.
     *
     * By comparing against the labels rather than by parsing them, so the two directions cannot
     * disagree - and so a translation can say anything it likes without breaking the editor.
     *
     * @param label what the dropdown is showing
     * @return the kind
     */
    public static Kind kindFor(String label)
    {
        for (Kind kind : Kind.values())
        {
            if (labelFor(kind).equals(label)) return kind;
        }

        // The enum name too, so a value stored or logged in the old form still resolves
        for (Kind kind : Kind.values())
        {
            if (kind.name().equals(label)) return kind;
        }

        return null;
    }

    /**
     * Whether this kind is something a route can DO, as against something it can ask about.
     *
     * AUTO_LOCOMOTIVE is "train X is standing at sensor 21", which is a fact rather than an
     * instruction: there is nothing for a route to carry out. It was offered in the command list
     * because the list offered every kind there is, and a route built with one would have been a
     * route with a sentence in it that cannot be obeyed.
     *
     * FEEDBACK is the second of these, and it was offered here for the same reason (OB-141).
     *
     * Adam: "a route command should not be able to contain s88 sensors. these should only be in the
     * conditions only." The model had always agreed with him - `RouteCommand.isConditionCommand()`
     * answers true for feedback - and only this method disagreed.
     *
     * WHAT IT DID WHEN SELECTED, which is the half worth writing down: nothing. `MarklinRoute.execRoute`
     * dispatches on a chain of isAccessory / isStop / isFunctionsOff / isAutonomyLightsOn / isLightsOn
     * / isLocomotiveSpeed / isLocomotiveDirection / isFunction / isRoute, with no isFeedback branch and
     * no final else, so the row fell through the lot. It sent nothing and logged nothing; its only
     * effect was the sleep the loop takes for every row, which made an s88 command a pure delay wearing
     * the name of an instruction. It was still saved, re-read and exported, so a route kept a sentence
     * in it that could never be obeyed.
     *
     * A row that already holds one still opens and still shows, the way an AUTO_LOCOMOTIVE row always
     * has - what changes is that no new one can be added.
     *
     * @param kind the kind
     * @return true if a route may contain it as a command
     */
    public static boolean canBeACommand(Kind kind)
    {
        return kind != Kind.AUTO_LOCOMOTIVE && kind != Kind.FEEDBACK;
    }

    /**
     * A setting this kind will accept, for when a row changes from one kind to another.
     *
     * The vocabularies do not overlap: an accessory is turn or straight, a feedback is on or off, a
     * direction is forward or backward.  So changing a row's kind has to change its setting too, or
     * the rebuilt command is refused and the change appears not to have happened.
     *
     * The FIRST of each pair, which is also the un-thrown, un-triggered, forward state - the one a
     * user is least likely to be surprised by finding there.
     */
    public static String defaultSettingFor(Kind kind)
    {
        switch (kind)
        {
            case ACCESSORY: return "straight";
            case SIGNAL: return "green";
            case THREE_WAY: return ThreeWaySwitch.wordFor(ThreeWaySwitch.Position.STRAIGHT);
            case FEEDBACK: return "off";
            case LOCOMOTIVE_DIRECTION: return "forward";
            case LOCOMOTIVE_SPEED: return "0";
            case FUNCTION: return "0:off";

            // AUTO_LOCOMOTIVE never reaches here, and this arm is kept only to say so (REL-C13,
            // VD9-C13).
            //
            // The argument that used to stand here was that refusing is better than offering sensor 1
            // to somebody who did not choose it.  That decision was reversed by `ef33f4a8`: the editor
            // offers exactly sensor 1, by name, on the condition side.
            //
            // The replacement sentence was wrong too - it claimed a COMMAND row of this kind is
            // refused until a sensor is typed, and there is no such state.  `canBeACommand` excludes
            // the kind outright, the commands dropdown is built from that predicate, and the Kind cell
            // is read-only on a row that already exists.  So this case is unreachable and identical to
            // `default`; it stays as the place that record lives.
            case AUTO_LOCOMOTIVE: return "";

            default: return "";
        }
    }

    /**
     * The pause a new row of this kind starts with, in milliseconds.
     *
     * Nothing waits by default; a three-way is the exception, because the pause between its two
     * motors is not a preference but the thing that stops the second starting while the first is
     * still moving.
     *
     * @param kind the kind
     * @return the delay to start with
     */
    public static int defaultDelayFor(Kind kind)
    {
        return kind == Kind.THREE_WAY ? ThreeWaySwitch.SETTLE : 0;
    }

    /**
     * Whether this kind means anything as a CONDITION.
     *
     * Route.evaluate understands accessory terms, feedback terms and auto-locomotive terms ("train X
     * is at s88 21").  A condition built from any other kind is permanently false, and a route
     * carrying one silently stops firing with no error anywhere - the condition editor offered all
     * seven kinds, which made that a two-click mistake.
     *
     * AUTO_LOCOMOTIVE **is** offered, and this paragraph used to say it was not (`REL-C13`).
     * `ef33f4a8` added it - *"an AUTONOMY CONDITION could be chosen and did nothing ... it starts on
     * the first locomotive now, like every other kind starts on something"* - and
     * `RouteEditorFrame.conditionKindChanged` supplies both halves the old sentence said the row could
     * not have: the first locomotive for the target, and sensor 1 for the setting.  So the editor does
     * build the row `ConditionRows`' own header uses as its example.
     *
     * **By method name, not by line** (`VD9-C12`): the first version of this paragraph cited
     * `RouteEditorFrame:3415-3419`, which was already wrong when it was copied here out of a review,
     * and a stale line number in a javadoc outlives every edit above it with nothing able to notice.
     *
     * The other NINE kinds are absent, and for the original reason: a condition built from one is
     * permanently false.  (This said four, which was right when `Kind` had seven members - it has
     * thirteen, and this predicate admits four of them.)  One already in a route is preserved
     * read-only, the way every other unsupported kind is.
     */
    public static boolean canBeACondition(Kind kind)
    {
        return kind == Kind.ACCESSORY || kind == Kind.SIGNAL
            || kind == Kind.FEEDBACK || kind == Kind.AUTO_LOCOMOTIVE;
    }

    /**
     * Whether this kind can carry a decoder type, so the editor can grey the cell for the rest.
     */
    public static boolean hasProtocol(Kind kind)
    {
        return kind == Kind.ACCESSORY || kind == Kind.SIGNAL || kind == Kind.THREE_WAY;
    }

    /**
     * Whether this kind can carry a delay.  Feedback, stop and functions-off cannot - RouteCommand
     * only writes a delay for accessory, speed, direction and function commands.
     */
    public static boolean hasDelay(Kind kind)
    {
        return kind == Kind.ACCESSORY || kind == Kind.SIGNAL || kind == Kind.LOCOMOTIVE_SPEED
            || kind == Kind.LOCOMOTIVE_DIRECTION || kind == Kind.FUNCTION
            || kind == Kind.THREE_WAY;
    }

    /**
     * Whether this kind takes a target at all, so the editor can grey the cell rather than offer a box
     * that does nothing.
     */
    public static boolean hasTarget(Kind kind)
    {
        return kind != Kind.STOP && kind != Kind.FUNCTIONS_OFF
            && kind != Kind.LIGHTS_ON && kind != Kind.AUTONOMY_LIGHTS_ON;
    }

    /**
     * Whether this kind takes a setting.
     */
    public static boolean hasSetting(Kind kind)
    {
        return kind != Kind.STOP && kind != Kind.FUNCTIONS_OFF
            && kind != Kind.LIGHTS_ON && kind != Kind.AUTONOMY_LIGHTS_ON
            && kind != Kind.ROUTE;
    }

    /**
     * Whether the FUNCTION kind's extra number is needed - it is the only kind with three parts, so it
     * borrows the target for the locomotive and packs "function:setting" into the setting.
     */
    public static boolean isFunction(Kind kind)
    {
        return kind == Kind.FUNCTION;
    }

    /**
     * The row behind a command, or null when the editor has no controls for it.
     *
     * @param command the command as stored
     */
    public static CommandRow of(RouteCommand command)
    {
        if (command == null) return null;

        if (command.isAccessory())
        {
            return new CommandRow(Kind.ACCESSORY, String.valueOf(command.getAddress()),
                command.getSetting() ? "turn" : "straight", command.getProtocol(), command.getDelay());
        }

        if (command.isFeedback())
        {
            return new CommandRow(Kind.FEEDBACK, String.valueOf(command.getAddress()),
                command.getSetting() ? "on" : "off");
        }

        if (command.isFunction())
        {
            // The only kind with three parts, so the function number rides with its setting
            return new CommandRow(Kind.FUNCTION, command.getName(),
                command.getFunction() + ":" + (command.getSetting() ? "on" : "off"),
                null, command.getDelay());
        }

        if (command.isLocomotiveSpeed())
        {
            return new CommandRow(Kind.LOCOMOTIVE_SPEED, command.getName(),
                String.valueOf(command.getSpeed()), null, command.getDelay());
        }

        if (command.isLocomotiveDirection())
        {
            return new CommandRow(Kind.LOCOMOTIVE_DIRECTION, command.getName(),
                command.getDirection() == Locomotive.locDirection.DIR_FORWARD ? "forward" : "backward",
                null, command.getDelay());
        }

        if (command.isStop()) return new CommandRow(Kind.STOP, "", "");

        if (command.isFunctionsOff()) return new CommandRow(Kind.FUNCTIONS_OFF, "", "");

        if (command.isLightsOn()) return new CommandRow(Kind.LIGHTS_ON, "", "");

        if (command.isAutonomyLightsOn()) return new CommandRow(Kind.AUTONOMY_LIGHTS_ON, "", "");

        if (command.isRoute()) return new CommandRow(Kind.ROUTE, command.getName(), "");

        if (command.isAutoLocomotive())
        {
            return new CommandRow(Kind.AUTO_LOCOMOTIVE, command.getName(),
                String.valueOf(command.getAddress()));
        }

        // Anything a later version adds.  Answering null keeps the command exactly as it was found
        // rather than losing it, which is why this method has always ended this way.
        return null;
    }

    /**
     * The command this row means, with the decoder type and delay the row carries.
     */
    public RouteCommand toCommand()
    {
        return toCommand(Accessory.DEFAULT_IMPLICIT_PROTOCOL);
    }

    /**
     * The commands this row means - usually one, and two for a three-way point.
     *
     * Every caller that writes a route out goes through here rather than through toCommand, because
     * a row that stands for two commands has no single command to return and asking for one would
     * quietly write out half a point.
     *
     * @param protocol the decoder type for a row that carries none of its own
     * @return the commands, in the order they must be sent
     * @throws IllegalArgumentException when the row cannot be made into commands
     */
    public java.util.List<RouteCommand> toCommands(Accessory.accessoryDecoderType protocol)
    {
        java.util.List<RouteCommand> out = new java.util.ArrayList<>();

        if (kind == Kind.THREE_WAY)
        {
            out.addAll(ThreeWaySwitch.expand(number(target, "route.wordAddress"),
                this.protocol != null ? this.protocol
                    : (protocol != null ? protocol : Accessory.DEFAULT_IMPLICIT_PROTOCOL),
                ThreeWaySwitch.positionFor(setting),
                delay > 0 ? delay : ThreeWaySwitch.SETTLE));

            return out;
        }

        out.add(toCommand(protocol));

        return out;
    }

    /**
     * The command this row means.
     *
     * @param protocol the decoder type to give an accessory that does not carry one of its own - a row
     *        the user built from scratch.  A row read from an existing command keeps ITS protocol, so
     *        opening a DCC route and saving it cannot silently move it to MM2.
     * @throws IllegalArgumentException when the row cannot be made into a command, so the editor can
     *         say which row is wrong instead of saving a broken route
     */
    public RouteCommand toCommand(Accessory.accessoryDecoderType protocol)
    {
        RouteCommand command = build(this.protocol != null ? this.protocol
            : (protocol != null ? protocol : Accessory.DEFAULT_IMPLICIT_PROTOCOL));

        // Set only when there is one: "no delay" is the absence of the key, and writing a zero makes a
        // second representation of the same command that compares unequal to the first
        if (delay > 0) command.setDelay(delay);

        return command;
    }

    private RouteCommand build(Accessory.accessoryDecoderType protocol)
    {
        switch (kind)
        {
            // A point is two commands, and there is no honest single one to hand back.  Reached only
            // by a caller that has not been moved to toCommands, so it says so rather than writing
            // out the first half of a point and losing the second.
            case THREE_WAY:
                throw new IllegalArgumentException(I18n.t("route.errorThreeWayNeedsBoth"));

            case STOP:
                return RouteCommand.RouteCommandStop();

            case FUNCTIONS_OFF:
                return RouteCommand.RouteCommandFunctionsOff();

            case LIGHTS_ON:
                return RouteCommand.RouteCommandLightsOn();

            case AUTONOMY_LIGHTS_ON:
                return RouteCommand.RouteCommandAutonomyLightsOn();

            case ROUTE:
                requireName();
                return RouteCommand.RouteCommandRoute(target);

            case AUTO_LOCOMOTIVE:
                requireName();
                return RouteCommand.RouteCommandAutoLocomotive(target,
                    number(setting, "route.wordAddress"));

            // One command either way.  The two kinds differ in the words the editor offers and in
            // nothing else, and oneOf below has always accepted all four.
            case SIGNAL:
            case ACCESSORY:
                // Red and green as well as turn and straight.  A signal and a switch are the same
                // device here - the type only decides which picture is drawn - so the same two states
                // have two pairs of names, and Accessory has always understood all four.  The editor
                // understood two, so a route built against a signal in the words a signal uses was
                // refused.
                return RouteCommand.RouteCommandAccessory(number(target, "route.wordAddress"), protocol,
                    oneOf(setting, "turn", "straight", "red", "green"));

            case FEEDBACK:
                return RouteCommand.RouteCommandFeedback(number(target, "route.wordAddress"),
                    oneOf(setting, "on", "off"));

            case LOCOMOTIVE_SPEED:
                requireName();
                return RouteCommand.RouteCommandLocomotiveSpeed(target, number(setting, "route.wordSpeed"));

            case LOCOMOTIVE_DIRECTION:
                requireName();
                return RouteCommand.RouteCommandLocomotiveDirection(target,
                    oneOf(setting, "backward", "forward")
                        ? Locomotive.locDirection.DIR_BACKWARD
                        : Locomotive.locDirection.DIR_FORWARD);

            case FUNCTION:
            {
                requireName();

                int colon = setting.indexOf(':');

                if (colon < 0)
                {
                            throw new IllegalArgumentException(
                        I18n.t("route.errorFunctionNeedsNumberAndSetting"));
                }

                return RouteCommand.RouteCommandFunction(target,
                    number(setting.substring(0, colon), "route.wordFunctionNumber"),
                    oneOf(setting.substring(colon + 1), "on", "off"));
            }

            default:
                throw new IllegalArgumentException("no controls for " + kind);
        }
    }

    /**
     * True for the first word, false for the second, and a refusal for anything else.
     *
     * The setting column is typed rather than chosen, and the old reading was
     * `"backward".equalsIgnoreCase(setting) ? BACKWARD : FORWARD` - so a user who typed "backwards",
     * or "back", or mis-hit a key, got FORWARD. Silently, with the table still showing what they had
     * typed, and the locomotive running the other way when the route fired.
     *
     * That is precisely the failure RouteCommand.fromLine was hardened against, in a class whose whole
     * purpose is to take the syntax risk away. An address typo is already refused with a dialog naming
     * the row; a direction typo has to be refused the same way rather than quietly guessed.
     *
     * @throws IllegalArgumentException when the text is neither
     */
    /**
     * The same, with a second name for each state.
     *
     * A signal's red is a switch's turn: one device, one pair of states, two vocabularies. Rather
     * than pick one and make the other wrong, both are accepted and the editor offers whichever
     * belongs to the thing at that address.
     *
     * @param setting what the row says
     * @param whenTrue the first name for the true state
     * @param whenFalse the first name for the false state
     * @param alsoTrue another name for the true state
     * @param alsoFalse another name for the false state
     * @return the state, or a refusal naming what was expected
     */
    private static boolean oneOf(String setting, String whenTrue, String whenFalse,
        String alsoTrue, String alsoFalse)
    {
        String said = setting == null ? "" : setting.trim().toLowerCase();

        if (said.equals(alsoTrue.toLowerCase())) return true;
        if (said.equals(alsoFalse.toLowerCase())) return false;

        return oneOf(setting, whenTrue, whenFalse);
    }

    private static boolean oneOf(String setting, String whenTrue, String whenFalse)
    {
        String text = setting == null ? "" : setting.trim();

        if (whenTrue.equalsIgnoreCase(text)) return true;
        if (whenFalse.equalsIgnoreCase(text)) return false;

        throw new IllegalArgumentException(
            I18n.f("route.errorSettingNotOneOf", setting, whenTrue, whenFalse));
    }

    private void requireName()
    {
        if (target.trim().isEmpty())
        {
            throw new IllegalArgumentException(I18n.t("route.errorRowNeedsALocomotive"));
        }
    }

    /**
     * A number from a cell, or a refusal naming what was expected.
     *
     * @param what a MESSAGE KEY, not a word - this text is shown to the user, in an editor whose every
     *        other string is translated
     */
    private static int number(String text, String what)
    {
        try
        {
            return Integer.parseInt(text.trim());
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException(I18n.f("route.errorNotA", text, I18n.t(what)));
        }
    }

    /**
     * The rows behind a list of commands, with an entry per command.
     *
     * A command with no controls yields a null entry, so the caller keeps the original at that
     * position rather than dropping it.
     */
    public static List<CommandRow> of(List<RouteCommand> commands)
    {
        List<CommandRow> rows = new ArrayList<>();

        if (commands == null) return rows;

        for (RouteCommand command : commands) rows.add(of(command));

        return rows;
    }

    @Override
    public String toString()
    {
        return kind + (target.isEmpty() ? "" : " " + target) + (setting.isEmpty() ? "" : " " + setting);
    }
}
