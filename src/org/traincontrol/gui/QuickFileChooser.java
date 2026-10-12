package org.traincontrol.gui;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.Icon;
import javax.swing.filechooser.FileSystemView;

/**
 * A file chooser whose Look In list is asked of Windows once, ahead of time, rather than every time (OB-323; Adam,
 * 2026-10-05: *"there is an odd (brief but noticeable) delay before the file chooser opens for Layouts -> Open
 * Layout... (half a second to one second or so)"*; and *"we don't want to sacrifice UX"*).
 *
 * Swing's chooser lists the Desktop and This PC through the shell - over COM, item by item - for that list, when it is
 * built and again at every change of folder.  Measured on Adam's PC, with a Desktop of 71 items in OneDrive: 1.0 to 1.8 s
 * each, so Open Layout's chooser cost two of them before it showed, and every step up a folder inside it another.
 *
 * The list is the same one: the shell's own answer - Recent Items, Desktop, Documents, This PC and its drives, Network,
 * Libraries and the user's folders - asked off the event thread as the window opens, and kept until a drive comes or goes
 * or a Desktop folder changes, when the next chooser asks again.  Swing asks the shell itself only for a chooser on the shell's own view of the files,
 * so this one carries a view of its own, which hands Swing the answer among its roots; everything else - the files, their
 * names and icons, which folder is above which - is the shell's as before.
 *
 * Every chooser in TrainControl is one of these - `testSwingSpeaksOurLanguage` looks.
 *
 * @author Adam
 */
public class QuickFileChooser extends javax.swing.JFileChooser
{
    /** The shell's own view of the files, which every chooser used. */
    private static final FileSystemView SHELL = FileSystemView.getFileSystemView();

    /** The Look In list's folders, as the shell last gave them, or null before it has. */
    private static volatile File[] places = null;

    /** What said the shell's answer could change - `landmarks` - when the shell was asked for `places`. */
    private static volatile java.util.List<Object> landmarksThen = null;

    /** The Desktop folders, whose own children are most of the list - found by the first ask. */
    private static volatile java.util.List<File> desktops = java.util.Collections.emptyList();

    /** The ask under way, or the last one; asked at most once at a time. */
    private static FutureTask<File[]> asking = null;

    /** How many times the shell has been asked, for the claims. */
    private static final AtomicInteger asked = new AtomicInteger();

    /** A chooser at the user's default folder. */
    public QuickFileChooser()
    {
        super(new Places());
    }

    /**
     * A chooser at a folder.
     *
     * @param currentDirectoryPath the folder, or null for the user's default
     */
    public QuickFileChooser(String currentDirectoryPath)
    {
        super(currentDirectoryPath, new Places());
    }

    /**
     * The folder as the shell's own, and a shortcut's target in place of the shortcut (RSA38-B2, RSA38-C1): what Swing does
     * itself for a chooser on the shell's view, and this one is not on it.  Followed, a shortcut to a folder - in Recent
     * Items, or the user's own - opens the folder, where it threw on the event thread and left the chooser listing the
     * folder before; and the shell's folder is what gives the details view the shell's columns - name, date, type and
     * size - for the shell's values, which came under Swing's three headings out of step with them.
     *
     * @param dir the folder
     */
    @Override
    public void setCurrentDirectory(File dir)
    {
        super.setCurrentDirectory(theShellsFolder(dir));
    }

    /**
     * Asks the shell for the Look In list off the event thread, unless it is being asked already - as the window opens, so
     * the first chooser has it.
     */
    public static void askTheShellAhead()
    {
        theAsk();
    }

    /**
     * The same, and the icons of what is in these folders too, so a chooser opening in one paints from the kept icons
     * rather than asking the shell for each on the event thread (MT-692's note: *"before the selector opens, the UI
     * briefly freezes"* - measured, half of a third of a second, each icon fetched as the dialog first painted).
     *
     * @param folders the folders the choosers open in; any that is no folder is passed over
     */
    public static void askTheShellAhead(File... folders)
    {
        theAsk();

        if (folders == null || folders.length == 0) return;

        Thread icons = new Thread(() ->
        {
            for (File folder : folders)
            {
                if (folder == null || !folder.isDirectory()) continue;

                try
                {
                    File[] inside = SHELL.getFiles(folder, true);

                    for (int i = 0; inside != null && i < inside.length && i < ICONS_AHEAD; i++)
                    {
                        Places.iconOf(inside[i]);
                    }
                }
                catch (RuntimeException e)
                {
                    // a folder the shell will not list is simply not fetched ahead
                }
            }
        }, "file chooser icons");

        icons.setDaemon(true);
        icons.start();
    }

    /** How many items of a folder have their icons fetched ahead - a long folder's first screenful and more. */
    private static final int ICONS_AHEAD = 400;

    /**
     * The ask under way, or one started now.
     *
     * @return what it will answer
     */
    private static FutureTask<File[]> theAsk()
    {
        synchronized (QuickFileChooser.class)
        {
            if (asking != null && !asking.isDone()) return asking;

            final FutureTask<File[]> ask = new FutureTask<>(() ->
            {
                if (desktops.isEmpty()) desktops = theDesktops();

                java.util.List<Object> now = landmarks();

                File[] found = theShellsPlaces();

                if (found != null)
                {
                    places = found;
                    landmarksThen = now;
                }

                return found;
            });

            asking = ask;

            Thread thread = new Thread(ask, "file chooser places");

            thread.setDaemon(true);
            thread.start();

            return ask;
        }
    }

    /**
     * The Look In list's folders: the shell's last answer while its landmarks are as they were then, and otherwise the ask
     * under way or one asked now, waited for - once, and never two at a time.
     *
     * @return the folders, or null where the shell gives none
     */
    private static File[] thePlaces()
    {
        File[] known = places;

        if (known != null && landmarks().equals(landmarksThen)) return known;

        try
        {
            File[] got = theAsk().get();

            return got != null ? got : known;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();

            return known;
        }
        catch (java.util.concurrent.ExecutionException e)
        {
            return known;
        }
    }

    /**
     * What says the shell's answer may have changed, each read without asking the shell: the drives, and the folders on
     * each Desktop (RSA39-C1) - most of the list is the Desktop's own folders, and a folder made on it, removed or renamed
     * was not followed while the drives stayed the same.  Its folders, not when it last changed (RSA40-C3): a file saved
     * to the Desktop changes that, and the list holds no files, so the next chooser waited on the shell for nothing.
     *
     * @return the drives, then the names of each Desktop's folders
     */
    private static java.util.List<Object> landmarks()
    {
        java.util.List<Object> out = new java.util.ArrayList<>();

        out.add(drives());

        for (File desktop : desktops)
        {
            File[] folders = desktop.listFiles(File::isDirectory);

            java.util.Set<String> names = new java.util.TreeSet<>();

            if (folders != null) for (File folder : folders) names.add(folder.getName());

            out.add(names);
        }

        return out;
    }

    /**
     * The Desktop folders: the user's, as the shell names it, and the one every user shares.
     *
     * @return those there are
     */
    private static java.util.List<File> theDesktops()
    {
        java.util.List<File> out = new java.util.ArrayList<>();

        try
        {
            File home = SHELL.getHomeDirectory();

            if (home != null && new File(home.getPath()).isDirectory()) out.add(new File(home.getPath()));
        }
        catch (RuntimeException e)
        {
            // none named
        }

        String shared = System.getenv("PUBLIC");

        if (shared != null && new File(shared, "Desktop").isDirectory()) out.add(new File(shared, "Desktop"));

        return out;
    }

    /** The drives there are now - a list of letters, which asks the shell nothing. */
    private static Set<File> drives()
    {
        File[] roots = File.listRoots();

        return roots == null ? new HashSet<File>() : new HashSet<>(Arrays.asList(roots));
    }

    /**
     * The Look In list's folders as the shell gives them - what Swing asked for itself - or null where it gives none.
     * Through reflection, as everything of `sun.awt.shell` here: it is not on the compiler's public list.
     */
    private static File[] theShellsPlaces()
    {
        asked.incrementAndGet();

        try
        {
            Object got = Class.forName("sun.awt.shell.ShellFolder").getMethod("get", String.class)
                .invoke(null, "fileChooserComboBoxFolders");

            return got instanceof File[] && ((File[]) got).length > 0 ? (File[]) got : null;
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e)
        {
            return null;
        }
    }

    /**
     * A folder as the shell's own, following a shortcut to where it leads - or the folder as it was, where the shell
     * cannot say.
     *
     * @param dir a folder, or null
     * @return the shell's folder, or a shortcut's target
     */
    private static File theShellsFolder(File dir)
    {
        if (dir == null) return null;

        try
        {
            Class<?> shell = Class.forName("sun.awt.shell.ShellFolder");

            Object folder = shell.getMethod("getShellFolder", File.class).invoke(null, dir);

            if (Boolean.TRUE.equals(shell.getMethod("isLink").invoke(folder)))
            {
                Object to = shell.getMethod("getLinkLocation").invoke(folder);

                if (to instanceof File) folder = shell.getMethod("getShellFolder", File.class).invoke(null, to);
            }

            return folder instanceof File ? (File) folder : dir;
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e)
        {
            return dir;
        }
    }

    /**
     * The shell's view of the files, with the Look In list's folders among its roots - where Swing looks for them when it
     * is not to ask the shell itself.  Which folder is a root for going up is still the shell's answer.
     */
    static final class Places extends FileSystemView
    {
        @Override
        public File[] getRoots()
        {
            File[] known = thePlaces();

            return known != null ? known.clone() : SHELL.getRoots();
        }

        @Override
        public boolean isRoot(File f)
        {
            return SHELL.isRoot(f);
        }

        @Override
        public File createNewFolder(File containingDir) throws IOException
        {
            return SHELL.createNewFolder(containingDir);
        }

        @Override
        public Boolean isTraversable(File f)
        {
            return SHELL.isTraversable(f);
        }

        @Override
        public String getSystemDisplayName(File f)
        {
            return SHELL.getSystemDisplayName(f);
        }

        @Override
        public String getSystemTypeDescription(File f)
        {
            return SHELL.getSystemTypeDescription(f);
        }

        @Override
        public Icon getSystemIcon(File f)
        {
            return iconOf(f);
        }

        /**
         * The icon the shell gives a file, kept once given (MT-692's note): a chooser asked the shell for each item's icon
         * on the event thread as it first painted, and every chooser asked again.
         *
         * @param f a file or folder, or null
         * @return its icon, or null
         */
        static Icon iconOf(File f)
        {
            if (f == null) return null;

            String key = f.getPath();

            Icon known = ICONS.get(key);

            if (known != null) return known;

            Icon given = SHELL.getSystemIcon(f);

            if (given != null) ICONS.put(key, given);

            return given;
        }

        /** The icons the shell has given, by path - the most recently used few thousand. */
        private static final java.util.Map<String, Icon> ICONS = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, Icon>(512, 0.75f, true)
            {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Icon> eldest)
                {
                    return size() > 4000;
                }
            });

        @Override
        public boolean isParent(File folder, File file)
        {
            return SHELL.isParent(folder, file);
        }

        @Override
        public File getChild(File parent, String fileName)
        {
            return SHELL.getChild(parent, fileName);
        }

        @Override
        public boolean isFileSystem(File f)
        {
            return SHELL.isFileSystem(f);
        }

        @Override
        public boolean isHiddenFile(File f)
        {
            return SHELL.isHiddenFile(f);
        }

        @Override
        public boolean isFileSystemRoot(File dir)
        {
            return SHELL.isFileSystemRoot(dir);
        }

        @Override
        public boolean isDrive(File dir)
        {
            return SHELL.isDrive(dir);
        }

        @Override
        public boolean isFloppyDrive(File dir)
        {
            return SHELL.isFloppyDrive(dir);
        }

        @Override
        public boolean isComputerNode(File dir)
        {
            return SHELL.isComputerNode(dir);
        }

        @Override
        public File getHomeDirectory()
        {
            return SHELL.getHomeDirectory();
        }

        @Override
        public File getDefaultDirectory()
        {
            return SHELL.getDefaultDirectory();
        }

        @Override
        public File createFileObject(File dir, String filename)
        {
            return SHELL.createFileObject(dir, filename);
        }

        @Override
        public File createFileObject(String path)
        {
            return SHELL.createFileObject(path);
        }

        @Override
        public File[] getFiles(File dir, boolean useFileHiding)
        {
            return SHELL.getFiles(dir, useFileHiding);
        }

        @Override
        public File getParentDirectory(File dir)
        {
            return SHELL.getParentDirectory(dir);
        }
    }
}
