package examples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * Drives one locomotive: direction, speed, lights and other functions, the settings it remembers, and the facts it
 * keeps about its own use.
 *
 * Shows `goForward`, `goBackward`, `switchDirection`, `goingForward`, `goingBackward`, `setSpeed`, `getSpeed`, `stop`,
 * `setF`, `getF`, `toggleF`, `lightsOn`, `lightsOff`, `validF`, `getNumF`, `getFunctionState`, `functionsOff`,
 * `setPreferredSpeed`, `applyPreferredSpeed`, `savePreferredFunctions`, `applyPreferredFunctions`,
 * `captureCustomizations` with its `getTypes`, `getNumPaths` and `getTotalRuntime`.
 */
public class DriveALocomotive
{
    /** The locomotive the example uses, made if the station does not have it. */
    public static final String NAME = "Example locomotive";

    /**
     * @param station the Central Station to drive
     * @return a line of text for each thing read back from the locomotive
     * @throws Exception when a command fails
     */
    public static List<String> run(MarklinControlStation station) throws Exception
    {
        List<String> found = new ArrayList<>();

        // Use the locomotive if the station already has one of this name.  Otherwise add a plain Marklin Motorola
        // locomotive at address 61, which every station accepts.  On your own railway, give the name of one of your
        // locomotives instead.
        MarklinLocomotive loco = station.getLocByName(NAME);

        if (loco == null) loco = station.newMM2Locomotive(NAME, 61);

        //
        // Direction.  Set it before the speed: a locomotive stops when it turns round.
        //
        // A station answers every command a moment later, and the locomotive records the answer.  Reading a state
        // back at once can still see an earlier answer, so the example waits a moment (`delay`, in milliseconds)
        // before each reading.  It is only needed when you read back right after a command.
        //
        loco.goBackward().delay(100);

        found.add("after goBackward: going backward " + loco.goingBackward() + ", going forward " + loco.goingForward());

        loco.goForward().delay(100);

        found.add("after goForward: going backward " + loco.goingBackward() + ", going forward " + loco.goingForward());

        // Turn it round, and back again.  Every command gives back the locomotive, so they can be chained.
        loco.switchDirection().delay(100).switchDirection().delay(100);

        found.add("after turning round twice: going forward " + loco.goingForward());

        //
        // Speed, from 0 to 100 percent
        //
        loco.setSpeed(40).delay(100);

        found.add("after setSpeed(40): speed " + loco.getSpeed());

        // The same as setSpeed(0)
        loco.stop().delay(100);

        found.add("after stop: speed " + loco.getSpeed());

        //
        // Functions.  F0 is the light.  A locomotive on the Motorola protocol has F0 to F4; other kinds have more,
        // and getNumF says how many.
        //
        loco.lightsOn().setF(2, true).delay(100);

        found.add("light " + loco.getF(0) + ", F1 " + loco.getF(1) + ", F2 " + loco.getF(2) + " of " + loco.getNumF()
            + " functions");

        // A function the locomotive does not have is never on, and asking is safe.
        found.add("function " + loco.getNumF() + " exists: " + loco.validF(loco.getNumF()));

        // A pulse: on for 100 milliseconds, then off.  Used for a horn or a coupler.
        loco.toggleF(3, 100).delay(100);

        found.add("after a pulse on F3: " + loco.getF(3));

        // All the function states at once, F0 first
        found.add("all functions: " + Arrays.toString(loco.getFunctionState()));

        loco.lightsOff().delay(100);

        found.add("after lightsOff: light " + loco.getF(0));

        //
        // Settings it remembers.  They are kept with the station's database, and the autonomy uses the preferred
        // speed when it starts a train.
        //
        loco.setPreferredSpeed(30);
        loco.savePreferredFunctions();

        // Put everything off, then bring the remembered setup back
        loco.functionsOff().delay(100);

        found.add("after functionsOff: F2 " + loco.getF(2));

        loco.applyPreferredFunctions().applyPreferredSpeed().delay(100);

        found.add("after applying the preferred setup: F2 " + loco.getF(2) + ", speed " + loco.getSpeed());

        // The function types decide which icon and behaviour each button has.  A copy is returned, so reading them
        // changes nothing.
        found.add("function types: " + Arrays.toString(loco.captureCustomizations().getTypes()));

        //
        // What the locomotive remembers about its use: journeys made by the autonomy, and the time it has run
        //
        found.add("journeys run: " + loco.getNumPaths() + ", total run time: " + loco.getTotalRuntime() + " ms");

        // Leave it standing with everything off
        loco.stop().functionsOff().delay(100);

        return found;
    }

    public static void main(String[] args) throws Exception
    {
        // Change this to your own station to drive your railway (see SimulatedStation).
        MarklinControlStation station = SimulatedStation.open();

        for (String line : run(station))
        {
            System.out.println(line);
        }

        System.exit(0);
    }
}
