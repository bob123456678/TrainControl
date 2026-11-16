package support;

import java.security.Permission;
import java.util.ArrayList;
import java.util.List;

/**
 * A security manager that refuses, and records, an exit (and with `true`, a process started) while a claim runs, so a
 * close the claim did not mean to cause cannot end the test JVM.  Everything else is allowed.  Install it with
 * `System.setSecurityManager` and put the old one (null) back.
 */
public final class NoExit extends SecurityManager
{
    private final List<String> refused = java.util.Collections.synchronizedList(new ArrayList<>());

    private final boolean noProcesses;

    public NoExit()
    {
        this(false);
    }

    public NoExit(boolean alsoNoProcesses)
    {
        noProcesses = alsoNoProcesses;
    }

    /** Each refusal in order: "exit 0", or "started <command>". */
    public List<String> refused()
    {
        synchronized (refused)
        {
            return new ArrayList<>(refused);
        }
    }

    /** The statuses of the exits refused, in order. */
    public List<Integer> exits()
    {
        List<Integer> statuses = new ArrayList<>();

        for (String each : refused())
        {
            if (each.startsWith("exit ")) statuses.add(Integer.valueOf(each.substring("exit ".length())));
        }

        return statuses;
    }

    @Override
    public void checkExit(int status)
    {
        refused.add("exit " + status);

        throw new SecurityException("no exit in a test");
    }

    @Override
    public void checkExec(String cmd)
    {
        if (!noProcesses) return;

        refused.add("started " + cmd);

        throw new SecurityException("no process is started in a test: " + cmd);
    }

    @Override
    public void checkPermission(Permission permission)
    {
    }

    @Override
    public void checkPermission(Permission permission, Object context)
    {
    }
}
