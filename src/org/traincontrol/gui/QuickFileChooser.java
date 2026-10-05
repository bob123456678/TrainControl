package org.traincontrol.gui;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * Libraries and the user's folders - asked off the event thread as the window opens and again after each chooser closes,
 * so a drive plugged in since is there the next time.  Swing is told not to ask for it itself, and finds it among this
 * chooser's roots instead; everything else - the files, their names and icons, which folder is above which - is the
 * shell's as before.  A chooser opened before the first answer is in asks for it there and then, as Swing did.
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

    /** Whether the shell is being asked now, so it is asked once at a time. */
    private static final AtomicBoolean asking = new AtomicBoolean();

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

    @Override
    public void updateUI()
    {
        // NOT THE SHELL'S LIST, ASKED AGAIN AT EVERY FOLDER: Swing reads this as the UI is built, which the constructor does
        putClientProperty("FileChooser.useShellFolder", Boolean.FALSE);

        super.updateUI();
    }

    @Override
    public int showDialog(java.awt.Component parent, String approveButtonText)
    {
        try
        {
            return super.showDialog(parent, approveButtonText);
        }
        finally
        {
            // and asked again for the next one, now nobody is waiting on the shell
            askTheShellAhead();
        }
    }

    /**
     * Asks the shell for the Look In list off the event thread, unless it is being asked already: as the window opens, so
     * the first chooser has it, and after each chooser closes.
     */
    public static void askTheShellAhead()
    {
        if (!asking.compareAndSet(false, true)) return;

        Thread ask = new Thread(() ->
        {
            try
            {
                File[] found = theShellsPlaces();

                if (found != null) places = found;
            }
            finally
            {
                asking.set(false);
            }
        }, "file chooser places");

        ask.setDaemon(true);
        ask.start();
    }

    /**
     * The Look In list's folders as the shell gives them - what Swing asked for itself - or null where it gives none.
     * Through reflection: `sun.awt.shell` is not on the compiler's public list.
     */
    private static File[] theShellsPlaces()
    {
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
     * The shell's view of the files, with the Look In list's folders among its roots - where Swing looks for them when it
     * is not to ask the shell itself.  Which folder is a root for going up is still the shell's answer.
     */
    static final class Places extends FileSystemView
    {
        @Override
        public File[] getRoots()
        {
            File[] known = places;

            if (known == null)
            {
                // NOT ASKED YET: asked here, as Swing would have, and kept
                known = theShellsPlaces();

                if (known != null) places = known;
            }

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
            return SHELL.getSystemIcon(f);
        }

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
