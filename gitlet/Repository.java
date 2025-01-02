package gitlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

import static gitlet.Utils.*;

/**
 * Represents a gitlet repository.
 * does at a high level.
 *
 * @author Zien
 */
public class Repository {
    /**
     * The current working directory.
     */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /**
     * The .gitlet directory.
     */
    public static final File GITLET_DIR = Utils.join(CWD, ".gitlet");

    /**
     * The staging area for added files.
     */
    private static final File STAGING_AREA = Utils.join(GITLET_DIR, "staging");
    /**
     * The directory for staged files to be added.
     */
    private static final File ADD_STAGE = Utils.join(STAGING_AREA, "add");
    /**
     * The directory for staged files to be removed.
     */
    private static final File REMOVE_STAGE = Utils.join(STAGING_AREA, "remove");

    /**
     * The file storing the current branch.
     */
    private static final File HEAD = Utils.join(GITLET_DIR, "HEAD");

    /**
     * The directory storing all commits.
     */
    private static final File COMMITS_DIR = Utils.join(GITLET_DIR, "commits");

    /**
     * The directory storing all file blobs.
     */
    private static final File BLOBS_DIR = Utils.join(GITLET_DIR, "blobs");

    /**
     * The directory storing the latest commit in each branch.
     */
    private static final File LATEST_COMMITS = Utils.join(GITLET_DIR, "latestCommits");

    /**
     * Initialize a new Gitlet version-control system.
     * This creates a .gitlet directory and initializes the repository with an initial commit.
     */
    public static void init() {
        if (GITLET_DIR.exists()) {
            System.out.println("A Gitlet version-control system already exists in the current directory.");
            return;
        }
        GITLET_DIR.mkdir();
        COMMITS_DIR.mkdir();
        BLOBS_DIR.mkdir();
        STAGING_AREA.mkdir();
        ADD_STAGE.mkdir();
        REMOVE_STAGE.mkdir();
        LATEST_COMMITS.mkdir();

        // Create initial commit
        Commit initialCommit = new Commit("initial commit");
        String initialCommitID = initialCommit.returnID();
        Utils.writeObject(Utils.join(COMMITS_DIR, initialCommitID), initialCommit);

        // Set up initial branch and its latest commit
        String initialBranch = "main";
        File initialBranchDir = Utils.join(LATEST_COMMITS, initialBranch);
        initialBranchDir.mkdir();
        Utils.writeObject(Utils.join(initialBranchDir, initialCommitID), initialCommit);

        // Set HEAD to point to the initial branch
        Utils.writeContents(HEAD, initialBranch);
    }

    /**
     * Add a single file from the user files directory to the staging area.
     *
     * @param fileName The name of the file to be added.
     */
    public static void addFileToStaging(String fileName) {
        File file = Utils.join(CWD, fileName);
        File stagedFile = Utils.join(ADD_STAGE, fileName);
        File removeFile = Utils.join(REMOVE_STAGE, fileName);

        if (!file.exists() || !file.isFile()) {
            System.out.println("File does not exist.");
            return;
        }

        byte[] currentContent = Utils.readContents(file);

        // Check if the file has the same content as the latest commit
        File latestCommitFile = getFileFromLatestCommit(fileName);
        if (latestCommitFile != null) {
            byte[] latestCommitContent = Utils.readContents(latestCommitFile);
            if (Arrays.equals(currentContent, latestCommitContent)) {
                // If the file is in the remove stage, remove it from there
                if (removeFile.exists()) {
                    removeFile.delete();
                }
                // If the file is in the add stage, remove it from there
                if (stagedFile.exists()) {
                    stagedFile.delete();
                }
                return;
            }
        }

        // Check if the file is already in the staging area with the same content
        if (stagedFile.exists()) {
            byte[] stagedContent = Utils.readContents(stagedFile);
            if (Arrays.equals(currentContent, stagedContent)) {
                System.out.println("File " + fileName + " is already staged with the same content.");
                return;
            }
        }

        // Remove the file from the remove stage if it is there
        if (removeFile.exists()) {
            removeFile.delete();
        }

        try {
            Files.copy(file.toPath(), stagedFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the file from the latest commit.
     *
     * @param fileName The name of the file.
     * @return The file from the latest commit or null if it does not exist.
     */
    private static File getFileFromLatestCommit(String fileName) {
        String currentBranch = Utils.readContentsAsString(HEAD).trim();
        File branchDir = Utils.join(LATEST_COMMITS, currentBranch);
        if (!branchDir.exists()) {
            return null;
        }

        // Read the latest commit ID from the branch directory
        File[] commitFiles = branchDir.listFiles();
        if (commitFiles == null || commitFiles.length == 0) {
            return null;
        }

        String latestCommitID = commitFiles[0].getName();
        Commit latestCommit = Utils.readObject(Utils.join(COMMITS_DIR, latestCommitID), Commit.class);
        Map<String, String> latestBlobs = latestCommit.getFileBlobs();

        if (latestBlobs.containsKey(fileName)) {
            String blobID = latestBlobs.get(fileName);
            return Utils.join(BLOBS_DIR, blobID);
        }

        return null;
    }


    public static void commit(String message) {
        if (message.equals("")) {
            System.out.println("Please enter a commit message.");
            return;
        }
        // Check if the staging area is empty
        File[] stagedAddFiles = ADD_STAGE.listFiles();
        File[] stagedRemoveFiles = REMOVE_STAGE.listFiles();

        if ((stagedAddFiles == null || stagedAddFiles.length == 0)
                && (stagedRemoveFiles == null || stagedRemoveFiles.length == 0)) {
            System.out.println("No changes added to the commit.");
            return;
        }

        // Read the current branch and get the latest commit ID
        String currentBranch = Utils.readContentsAsString(HEAD).trim();
        File branchDir = Utils.join(LATEST_COMMITS, currentBranch);
        if (!branchDir.exists() || branchDir.listFiles() == null || branchDir.listFiles().length == 0) {
            System.out.println("Branch directory is empty or does not exist.");
            return;
        }
        String latestCommitID = branchDir.listFiles()[0].getName();
        Commit latestCommit = Utils.readObject(Utils.join(COMMITS_DIR, latestCommitID), Commit.class);

        // Create a new fileBlobs map inheriting from the latest commit
        Map<String, String> newFileBlobs = new HashMap<>(latestCommit.getFileBlobs());

        // Add all files from the staging area to the new commit
        if (stagedAddFiles != null) {
            for (File file : stagedAddFiles) {
                String blobID = Utils.sha1(Utils.readContents(file));
                File blobFile = Utils.join(BLOBS_DIR, blobID);

                try {
                    Files.copy(file.toPath(), blobFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Update newFileBlobs with the staged file
                newFileBlobs.put(file.getName(), blobID);
            }
        }

        // Remove files in the staging remove area from the new commit
        if (stagedRemoveFiles != null) {
            for (File file : stagedRemoveFiles) {
                newFileBlobs.remove(file.getName());
            }
        }

        // Create a new commit
        Commit newCommit = new Commit(message, latestCommit);
        newCommit.setFileBlobs(newFileBlobs);
        String newCommitID = newCommit.returnID();
        Utils.writeObject(Utils.join(COMMITS_DIR, newCommitID), newCommit);

        // Update the latest commit ID for the current branch
        // Remove old commit ID file in the branch directory
        for (File file : branchDir.listFiles()) {
            file.delete();
        }
        File newCommitFile = Utils.join(branchDir, newCommitID);
        Utils.writeObject(newCommitFile, newCommit);

        // Clear the staging area
        if (stagedAddFiles != null) {
            for (File file : stagedAddFiles) {
                file.delete();
            }
        }
        if (stagedRemoveFiles != null) {
            for (File file : stagedRemoveFiles) {
                file.delete();
            }
        }
    }

    /**
     * Restore a file from the latest commit in the current branch.
     *
     * @param fileName The name of the file to be restored.
     */
    public static void restore(String fileName) {
        // Get the current branch and the latest commit ID
        String currentBranch = Utils.readContentsAsString(HEAD).trim();
        File branchDir = Utils.join(LATEST_COMMITS, currentBranch);
        if (!branchDir.exists() || branchDir.listFiles() == null || branchDir.listFiles().length == 0) {
            System.out.println("Branch directory is empty or does not exist.");
            return;
        }
        String latestCommitID = branchDir.listFiles()[0].getName();
        Commit latestCommit = Utils.readObject(Utils.join(COMMITS_DIR, latestCommitID), Commit.class);

        // Restore the file from the latest commit
        restoreFileFromCommit(latestCommit, fileName);
    }

    /**
     * Find the full commit ID based on a shortened prefix.
     *
     * @param prefix The shortened prefix of the commit ID.
     * @return The full commit ID if a unique match is found, otherwise null.
     */
    private static String findFullCommitID(String prefix) {
        List<String> allCommitIDs = Utils.plainFilenamesIn(COMMITS_DIR);
        String fullCommitID = null;

        for (String commitID : allCommitIDs) {
            if (commitID.startsWith(prefix)) {
                if (fullCommitID != null) {
                    // More than one commit ID matches the prefix
                    return null;
                }
                fullCommitID = commitID;
            }
        }

        return fullCommitID;
    }

    /**
     * Restore a file from a specific commit.
     *
     * @param commitID The ID of the commit.
     * @param fileName The name of the file to be restored.
     */
    public static void restore(String commitID, String fileName) {

        String fullCommitID = findFullCommitID(commitID);
        if (fullCommitID == null) {
            System.out.println("No commit with that id exists.");
            return;
        }

        File commitFile = Utils.join(COMMITS_DIR, fullCommitID);
        if (!commitFile.exists()) {
            System.out.println("No commit with that id exists.");
            return;
        }
        Commit commit = Utils.readObject(commitFile, Commit.class);

        // Restore the file from the specified commit
        restoreFileFromCommit(commit, fileName);
    }

    /**
     * Restore a file from the given commit.
     *
     * @param commit   The commit object to restore the file from.
     * @param fileName The name of the file to be restored.
     */
    private static void restoreFileFromCommit(Commit commit, String fileName) {
        Map<String, String> fileBlobs = commit.getFileBlobs();
        if (!fileBlobs.containsKey(fileName)) {
            System.out.println("File does not exist in that commit.");
            return;
        }

        // Get the blob ID and the corresponding file in the blobs directory
        String blobID = fileBlobs.get(fileName);
        File blobFile = Utils.join(BLOBS_DIR, blobID);
        File targetFile = Utils.join(CWD, fileName);

        try {
            // Copy the file from the blobs directory to the working directory
            Files.copy(blobFile.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Display the commit history starting from the current head commit.
     */
    public static void log() {
        // Read the current branch and get the latest commit ID
        String currentBranch = Utils.readContentsAsString(HEAD).trim();
        File branchDir = Utils.join(LATEST_COMMITS, currentBranch);
        if (!branchDir.exists() || branchDir.listFiles() == null || branchDir.listFiles().length == 0) {
            System.out.println("Branch directory is empty or does not exist.");
            return;
        }
        String latestCommitID = branchDir.listFiles()[0].getName();
        Commit currentCommit = Utils.readObject(Utils.join(COMMITS_DIR, latestCommitID), Commit.class);

        // Iterate through the commit history
        while (currentCommit != null) {
            printCommit(currentCommit);
            Commit parentCommit = currentCommit.getParent();
            currentCommit = parentCommit;
        }
    }

    /**
     * Print the details of a single commit.
     *
     * @param commit The commit object to be printed.
     */
    private static void printCommit(Commit commit) {
        System.out.println("===");
        System.out.println("commit " + commit.returnID());

        if (commit.isMerge()) {
            // 打印合并信息
            String mergeParentID = commit.getMergeParent().returnID();
            String firstParentID = commit.getParent().returnID().substring(0, 7);
            String secondParentID = mergeParentID.substring(0, 7);
            System.out.println("Merge: " + firstParentID + " " + secondParentID);
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z");
        dateFormat.setTimeZone(TimeZone.getDefault());
        String formattedDate = dateFormat.format(commit.getTimestamp());

        System.out.println("Date: " + formattedDate);
        System.out.println(commit.getMessage());
        System.out.println();
    }


    /**
     * Display information about all commits ever made.
     * Iterates through all files in the commits directory and prints their details.
     */
    public static void globalLog() {
        // Get all commit files
        List<String> commitFiles = Utils.plainFilenamesIn(COMMITS_DIR);
        if (commitFiles == null || commitFiles.isEmpty()) {
            System.out.println("No commits found.");
            return;
        }

        // Iterate through all commit files and print details
        for (String commitFileName : commitFiles) {
            File commitFile = Utils.join(COMMITS_DIR, commitFileName);
            Commit commit = Utils.readObject(commitFile, Commit.class);
            printCommit(commit);
        }
    }

    /**
     * Unstage the file if it is currently staged for addition.
     * If the file is tracked in the current commit, stage it for removal
     * and remove the file from the working directory if the user has not already done so.
     *
     * @param fileName The name of the file to be removed.
     */
    public static void rm(String fileName) {
        File file = Utils.join(CWD, fileName);
        File stagedFile = Utils.join(ADD_STAGE, fileName);
        File removeFile = Utils.join(REMOVE_STAGE, fileName);
        boolean isStagedForAddition = stagedFile.exists();

        // Get the latest commit
        String currentBranch = Utils.readContentsAsString(HEAD).trim();
        File branchDir = Utils.join(LATEST_COMMITS, currentBranch);
        if (!branchDir.exists() || branchDir.listFiles() == null || branchDir.listFiles().length == 0) {
            System.out.println("Branch directory is empty or does not exist.");
            return;
        }
        String latestCommitID = branchDir.listFiles()[0].getName();
        Commit latestCommit = Utils.readObject(Utils.join(COMMITS_DIR, latestCommitID), Commit.class);
        Map<String, String> latestBlobs = latestCommit.getFileBlobs();
        boolean isTrackedInCommit = latestBlobs.containsKey(fileName);

        if (!isStagedForAddition && !isTrackedInCommit) {
            System.out.println("No reason to remove the file.");
            return;
        }

        // If the file is already staged for removal, notify the user and return
        if (removeFile.exists()) {
            System.out.println("File " + fileName + " is already marked for removal.");
            return;
        }

        // Unstage the file if it is currently staged for addition
        if (isStagedForAddition) {
            stagedFile.delete();
        }

        // Stage it for removal and remove the file from the working directory if it is tracked in the current commit
        if (isTrackedInCommit) {
            Utils.writeContents(removeFile, fileName);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    public static void status() {
        if (!LATEST_COMMITS.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            return;
        }
        printBranches();
        printStagedFiles();
        printRemovedFiles();
        printModificationsNotStagedForCommit();
        printUntrackedFiles();
    }

    private static void printBranches() {
        System.out.println("=== Branches ===");
        String currentBranch = Utils.readContentsAsString(HEAD).trim();
        List<String> branches = listDirectories(LATEST_COMMITS);
        if (branches != null) {
            branches.sort(String::compareTo);
            for (String branch : branches) {
                if (branch.equals(currentBranch)) {
                    System.out.println("*" + branch);
                } else {
                    System.out.println(branch);
                }
            }
        }
        System.out.println();
    }

    private static void printStagedFiles() {
        System.out.println("=== Staged Files ===");
        List<String> stagedFiles = plainFilenamesIn(ADD_STAGE);
        printFileList(stagedFiles);
    }

    private static void printRemovedFiles() {
        System.out.println("=== Removed Files ===");
        List<String> removedFiles = plainFilenamesIn(REMOVE_STAGE);
        printFileList(removedFiles);
    }

    private static void printModificationsNotStagedForCommit() {
        System.out.println("=== Modifications Not Staged For Commit ===");
        Set<String> modifications = new HashSet<>();
        List<String> allFilesInCWD = plainFilenamesIn(CWD);
        List<String> stagedFiles = plainFilenamesIn(ADD_STAGE);
        String currentBranch = Utils.readContentsAsString(HEAD).trim();

        if (allFilesInCWD != null) {
            for (String fileName : allFilesInCWD) {
                File file = join(CWD, fileName);
                if (stagedFiles != null && stagedFiles.contains(fileName)) {
                    File stagedFile = join(ADD_STAGE, fileName);
                    if (!file.exists()) {
                        modifications.add(fileName + " (deleted)");
                    } else if (!Arrays.equals(readContents(file), readContents(stagedFile))) {
                        modifications.add(fileName + " (modified)");
                    }
                } else if (isTracked(fileName)) {
                    String currentBranchCommitID = plainFilenamesIn(
                            join(LATEST_COMMITS, currentBranch)
                    ).get(0);
                    Commit currentCommit = readObject(
                            join(COMMITS_DIR, currentBranchCommitID),
                            Commit.class
                    );
                    if (!file.exists()) {
                        modifications.add(fileName + " (deleted)");
                    } else if (!Arrays.equals(
                            readContents(file),
                            readContents(join(BLOBS_DIR, currentCommit.getFileBlobs().get(fileName)))
                    )) {
                        modifications.add(fileName + " (modified)");
                    }
                }
            }
        }

        List<String> sortedModifications = new ArrayList<>(modifications);
        sortedModifications.sort(String::compareTo);
        for (String mod : sortedModifications) {
            System.out.println(mod);
        }
        System.out.println();
    }

    private static void printUntrackedFiles() {
        System.out.println("=== Untracked Files ===");
        List<String> allFilesInCWD = plainFilenamesIn(CWD);
        List<String> stagedFiles = plainFilenamesIn(ADD_STAGE);
        List<String> removedFiles = plainFilenamesIn(REMOVE_STAGE);
        List<String> untrackedFiles = new ArrayList<>();

        if (allFilesInCWD != null) {
            for (String fileName : allFilesInCWD) {
                if ((stagedFiles == null || !stagedFiles.contains(fileName))
                        && (removedFiles == null || !removedFiles.contains(fileName))
                        && !isTracked(fileName)) {
                    untrackedFiles.add(fileName);
                }
            }
        }

        untrackedFiles.sort(String::compareTo);
        for (String file : untrackedFiles) {
            System.out.println(file);
        }
        System.out.println();
    }

    private static void printFileList(List<String> files) {
        if (files != null) {
            files.sort(String::compareTo);
            for (String file : files) {
                System.out.println(file);
            }
        }
        System.out.println();
    }

    /**
     * Helper method to determine if a file is tracked in the latest commit.
     *
     * @param fileName The name of the file.
     * @return true if the file is tracked, false otherwise.
     */
    private static boolean isTracked(String fileName) {
        String currentBranch = Utils.readContentsAsString(HEAD).trim();
        String latestCommitID = plainFilenamesIn(join(LATEST_COMMITS, currentBranch)).get(0);
        Commit latestCommit = readObject(join(COMMITS_DIR, latestCommitID), Commit.class);
        return latestCommit.getFileBlobs().containsKey(fileName);
    }


    /**
     * Helper method to list directories in a given directory.
     *
     * @param dir The directory to list directories in.
     * @return A list of directory names.
     */
    private static List<String> listDirectories(File dir) {
        File[] files = dir.listFiles(File::isDirectory);
        List<String> directories = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                directories.add(file.getName());
            }
        }
        return directories;
    }

    /**
     * Finds and prints the IDs of all commits that have the given commit message.
     * If multiple commits have the same message, their IDs are printed on separate lines.
     * If no commit with the given message exists, prints an appropriate error message.
     *
     * @param message The commit message to search for.
     */
    public static void find(String message) {
        // Get all commit files
        List<String> commitFiles = Utils.plainFilenamesIn(COMMITS_DIR);
        if (commitFiles == null || commitFiles.isEmpty()) {
            System.out.println("Found no commit with that message.");
            return;
        }

        boolean found = false;

        // Iterate through all commit files and check their messages
        for (String commitFileName : commitFiles) {
            File commitFile = Utils.join(COMMITS_DIR, commitFileName);
            Commit commit = Utils.readObject(commitFile, Commit.class);
            if (commit.getMessage().equals(message)) {
                System.out.println(commit.returnID());
                found = true;
            }
        }

        if (!found) {
            System.out.println("Found no commit with that message.");
        }
    }


    /**
     * Creates a new branch with the given name. The new branch starts at the
     * current commit of the current branch.
     *
     * @param branchName The name of the new branch to create.
     */
    public static void createNewBranch(String branchName) {
        File newBranchDir = Utils.join(LATEST_COMMITS, branchName);
        if (newBranchDir.exists()) {
            System.out.println("A branch with that name already exists.");
        }
        newBranchDir.mkdir();

        String currentBranch = Utils.readContentsAsString(HEAD).trim();
        File branchDir = Utils.join(LATEST_COMMITS, currentBranch);
        File[] commitFiles = branchDir.listFiles();
        if (commitFiles == null || commitFiles.length == 0) {
            System.out.println("No commits found in the current branch.");
            return;
        }
        String latestCommitID = commitFiles[0].getName();
        Commit latestCommit = Utils.readObject(Utils.join(COMMITS_DIR, latestCommitID), Commit.class);
        writeObject(join(newBranchDir, latestCommitID), latestCommit);

    }


    /**
     * Switches to the branch with the given name.
     * Takes all files in the commit at the head of the given branch,
     * and puts them in the working directory, overwriting the versions
     * of the files that are already there if they exist. Also, at the
     * end of this command, the given branch will now be considered the
     * current branch (HEAD). Any files that are tracked in the current
     * branch but are not present in the branch you are switching to are deleted.
     * The staging area is cleared, unless the given branch is the current branch.
     *
     * @param branchName The name of the branch to switch to.
     */
    public static void switchBranch(String branchName) {
        File branchDir = Utils.join(LATEST_COMMITS, branchName);

        // Check if the branch exists
        if (!branchDir.exists()) {
            System.out.println("No such branch exists.");
            return;
        }

        // Check if the branch is the current branch
        String currentBranch = Utils.readContentsAsString(HEAD).trim();
        if (branchName.equals(currentBranch)) {
            System.out.println("No need to switch to the current branch.");
            return;
        }

        // Check for untracked files that would be overwritten
        Commit currentCommit = getCurrentCommit();
        Commit targetCommit = getLatestCommit(branchDir);
        for (String fileName : Utils.plainFilenamesIn(CWD)) {
            if (!currentCommit.getFileBlobs().containsKey(fileName)
                    && targetCommit.getFileBlobs().containsKey(fileName)) {
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                return;
            }
        }

        // Clear the staging area
        clearStagingArea();

        // Get the files from the target commit and put them in the working directory
        for (Map.Entry<String, String> entry : targetCommit.getFileBlobs().entrySet()) {
            String fileName = entry.getKey();
            String blobID = entry.getValue();
            File blobFile = Utils.join(BLOBS_DIR, blobID);
            File targetFile = Utils.join(CWD, fileName);
            try {
                Files.copy(blobFile.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Delete files that are tracked in the current branch but not in the target branch
        for (String fileName : currentCommit.getFileBlobs().keySet()) {
            if (!targetCommit.getFileBlobs().containsKey(fileName)) {
                File fileToDelete = Utils.join(CWD, fileName);
                if (fileToDelete.exists()) {
                    fileToDelete.delete();
                }
            }
        }

        // Update HEAD to point to the new branch
        Utils.writeContents(HEAD, branchName);
    }

    /**
     * Helper method to get the latest commit of a branch.
     *
     * @param branchDir The directory of the branch.
     * @return The latest commit object.
     */
    private static Commit getLatestCommit(File branchDir) {
        File[] commitFiles = branchDir.listFiles();
        if (commitFiles == null || commitFiles.length == 0) {
            throw new IllegalStateException("Branch has no commits.");
        }
        String latestCommitID = commitFiles[0].getName();
        return Utils.readObject(Utils.join(COMMITS_DIR, latestCommitID), Commit.class);
    }

    /**
     * Helper method to get the current commit.
     *
     * @return The current commit object.
     */
    private static Commit getCurrentCommit() {
        String currentBranch = Utils.readContentsAsString(HEAD).trim();
        File branchDir = Utils.join(LATEST_COMMITS, currentBranch);
        return getLatestCommit(branchDir);
    }

    /**
     * Helper method to clear the staging area.
     */
    private static void clearStagingArea() {
        List<String> addStageFiles = Utils.plainFilenamesIn(ADD_STAGE);
        if (addStageFiles != null) {
            for (String fileName : addStageFiles) {
                File file = Utils.join(ADD_STAGE, fileName);
                file.delete();
            }
        }

        List<String> removeStageFiles = Utils.plainFilenamesIn(REMOVE_STAGE);
        if (removeStageFiles != null) {
            for (String fileName : removeStageFiles) {
                File file = Utils.join(REMOVE_STAGE, fileName);
                file.delete();
            }
        }
    }

    /**
     * Deletes the branch with the given name.
     * This only means to delete the pointer associated with the branch;
     * it does not mean to delete all commits that were created under the branch.
     *
     * @param branchName The name of the branch to delete.
     */
    public static void removeBranch(String branchName) {
        File branchDir = Utils.join(LATEST_COMMITS, branchName);

        // Check if the branch exists
        if (!branchDir.exists()) {
            System.out.println("A branch with that name does not exist.");
            return;
        }

        // Check if trying to remove the current branch
        String currentBranch = Utils.readContentsAsString(HEAD).trim();
        if (branchName.equals(currentBranch)) {
            System.out.println("Cannot remove the current branch.");
            return;
        }

        // Delete the branch directory
        for (File file : Objects.requireNonNull(branchDir.listFiles())) {
            file.delete();
        }
        branchDir.delete();
    }

    /**
     * Restores all the files tracked by the given commit.
     * Also moves the current branch’s head to that commit node.
     * The [commit id] may be abbreviated as for restore.
     * The staging area is cleared.
     *
     * @param commitID The commit ID to reset to.
     */
    public static void reset(String commitID) {
        File commitFile = Utils.join(COMMITS_DIR, commitID);

        // Check if the commit exists
        if (!commitFile.exists()) {
            System.out.println("No commit with that id exists.");
            return;
        }


        Commit targetCommit = Utils.readObject(commitFile, Commit.class);
        // Get the current commit
        String currentBranch = Utils.readContentsAsString(HEAD).trim();
        File branchDir = Utils.join(LATEST_COMMITS, currentBranch);
        String latestCommitID = branchDir.listFiles()[0].getName();
        Commit currentCommit = Utils.readObject(Utils.join(COMMITS_DIR, latestCommitID), Commit.class);

        // Check for untracked files that would be overwritten
        for (String fileName : Utils.plainFilenamesIn(CWD)) {
            if (!currentCommit.getFileBlobs().containsKey(fileName) && !Utils.join(CWD, fileName).isDirectory()
                    && targetCommit.getFileBlobs().containsKey(fileName)) {
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                return;
            }
        }

        // Clear the staging area
        clearStagingArea();

        // Restore the files from the target commit
        for (Map.Entry<String, String> entry : targetCommit.getFileBlobs().entrySet()) {
            String fileName = entry.getKey();
            String blobID = entry.getValue();
            File blobFile = Utils.join(BLOBS_DIR, blobID);
            File targetFile = Utils.join(CWD, fileName);
            try {
                Files.copy(blobFile.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Remove files in CWD that are not in the target commit
        for (String fileName : Utils.plainFilenamesIn(CWD)) {
            if (!targetCommit.getFileBlobs().containsKey(fileName) && !Utils.join(CWD, fileName).isDirectory()) {
                File fileToDelete = Utils.join(CWD, fileName);
                if (fileToDelete.exists()) {
                    fileToDelete.delete();
                }
            }
        }

        // Move the current branch’s head to the target commit
        File[] files = branchDir.listFiles();
        if (files != null && files.length > 0) {
            files[0].delete();
        } else {
            System.out.println("No latest commit ID in current branch, something wrong");
            return;
        }
        Utils.writeObject(Utils.join(branchDir, commitID), targetCommit);
    }

    public static void merge(String branchName) {

        // Check if there are uncommitted changes in the staging area
        if (Utils.plainFilenamesIn(ADD_STAGE).size() > 0 || Utils.plainFilenamesIn(REMOVE_STAGE).size() > 0) {
            System.out.println("You have uncommitted changes.");
            return;
        }

        // Get the current branch and the target branch
        String currentBranch = Utils.readContentsAsString(HEAD).trim();
        File givenBranchDir = Utils.join(LATEST_COMMITS, branchName);

        // Check if the target branch exists
        if (!givenBranchDir.exists()) {
            System.out.println("A branch with that name does not exist.");
            return;
        }

        // Check if trying to merge the current branch with itself
        if (branchName.equals(currentBranch)) {
            System.out.println("Cannot merge a branch with itself.");
            return;
        }

        // Find the split point
        Commit splitPoint = findSplitPoint(currentBranch, branchName);
        Commit currentCommit = getLatestCommit(Utils.join(LATEST_COMMITS, currentBranch));
        Commit givenCommit = getLatestCommit(givenBranchDir);

        // Handle cases where the split point is the current branch or the target branch
        if (splitPoint.returnID().equals(givenCommit.returnID())) {
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        }
        if (splitPoint.returnID().equals(currentCommit.returnID())) {
            switchBranch(branchName);
            System.out.println("Current branch fast-forwarded.");
            return;
        }

        // Check if untracked files will be overwritten or deleted
        List<String> untrackedFiles = getUntrackedFiles();
        for (String fileName : untrackedFiles) {
            if (Utils.join(CWD, fileName).exists()
                    && (givenCommit.getFileBlobs().containsKey(fileName)
                    || !splitPoint.getFileBlobs().containsKey(fileName))) {
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                return;
            }
        }

        // Handle files modified in the given branch but not in the current branch since the split point
        handleModifiedFilesInGivenButCurrentSinceSplitPoint(splitPoint, currentCommit, givenCommit);

        // Handle files modified in the same way in both branches since the split point
        handleSameModifiedFilesInBothBranchSinceSplitPoint(splitPoint, currentCommit, givenCommit);

        // Handle files that do not exist in the split point but only exist in the given branch
        handleFilesInGivenBranchOnlySinceSplitPoint(splitPoint, currentCommit, givenCommit);

        // Handle files that exist in the split point, not modified in the current branch, but do not exist in the given branch
        handleFilesInSplitPointButNotInGivenBranch(splitPoint, currentCommit, givenCommit);

        // Handle conflicting files
        boolean conflict = handleConflictingFiles(splitPoint, currentCommit, givenCommit);

        if (conflict) {
            System.out.println("Encountered a merge conflict.");
        }
        // Automatically commit the merge
        String message = "Merged " + branchName + " into " + currentBranch + ".";
        commitMerge(message, currentBranch, currentCommit, givenCommit);
    }

    private static List<String> getUntrackedFiles() {
        List<String> untrackedFiles = new ArrayList<>();
        List<String> allFilesInCWD = Utils.plainFilenamesIn(CWD);
        List<String> stagedFiles = Utils.plainFilenamesIn(ADD_STAGE);
        List<String> removedFiles = Utils.plainFilenamesIn(REMOVE_STAGE);

        for (String fileName : allFilesInCWD) {
            if (!stagedFiles.contains(fileName) && !removedFiles.contains(fileName) && !isTracked(fileName)) {
                untrackedFiles.add(fileName);
            }
        }
        return untrackedFiles;
    }

    private static boolean handleConflictingFiles(Commit splitPoint, Commit currentCommit, Commit givenCommit) {
        Map<String, String> splitBlobs = splitPoint.getFileBlobs();
        Map<String, String> currentBlobs = currentCommit.getFileBlobs();
        Map<String, String> givenBlobs = givenCommit.getFileBlobs();
        boolean conflict = false;

        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(splitBlobs.keySet());
        allFiles.addAll(currentBlobs.keySet());
        allFiles.addAll(givenBlobs.keySet());

        for (String fileName : allFiles) {
            String splitBlob = splitBlobs.get(fileName);
            String currentBlob = currentBlobs.get(fileName);
            String givenBlob = givenBlobs.get(fileName);

            boolean splitExists = splitBlob != null;
            boolean currentExists = currentBlob != null;
            boolean givenExists = givenBlob != null;

            boolean currentModified = splitExists && currentExists && !splitBlob.equals(currentBlob);
            boolean givenModified = splitExists && givenExists && !splitBlob.equals(givenBlob);

            // Case 1: Both current and given branches modified the file, and the contents are different
            if (currentModified && givenModified && !currentBlob.equals(givenBlob)) {
                conflict = true;
                handleConflict(fileName, currentBlob, givenBlob);
                // Case 2: The file does not exist in the split point but exists in both current and given branches, and the contents are different
            } else if (!splitExists && currentExists && givenExists && !currentBlob.equals(givenBlob)) {
                conflict = true;
                handleConflict(fileName, currentBlob, givenBlob);
                // Case 3: The file exists in the split point but was deleted in the current branch and modified in the given branch
            } else if (splitExists && !currentExists && givenModified) {
                conflict = true;
                handleConflict(fileName, null, givenBlob);
                // Case 4: The file exists in the split point but was modified in the current branch and deleted in the given branch
            } else if (splitExists && currentModified && !givenExists) {
                conflict = true;
                handleConflict(fileName, currentBlob, null);
            }
        }

        return conflict;
    }

    private static void handleConflict(String fileName, String currentBlob, String givenBlob) {
        String currentContent = "";
        if (currentBlob == null) {
            currentContent = "";
        } else {
            currentContent = Utils.readContentsAsString(Utils.join(BLOBS_DIR, currentBlob));
        }
        String givenContent = givenBlob == null ? "" : Utils.readContentsAsString(Utils.join(BLOBS_DIR, givenBlob));

        String conflictContent = "<<<<<<< HEAD\n" + currentContent + "=======\n" + givenContent + ">>>>>>>\n";
        File targetFile = Utils.join(CWD, fileName);
        Utils.writeContents(targetFile, conflictContent);

        // Stage the conflict file for addition
        stageFileForAddition(fileName);
    }

    private static void commitMerge(String message, String cBranch, Commit c, Commit g) {

        // Check if the staging area is empty
        File[] stagedAddFiles = ADD_STAGE.listFiles();
        File[] stagedRemoveFiles = REMOVE_STAGE.listFiles();

        // Create a new fileBlobs map inheriting from the latest commit
        Map<String, String> newFileBlobs = new HashMap<>(c.getFileBlobs());

        // Add all files from the staging area to the new commit
        if (stagedAddFiles != null) {
            for (File file : stagedAddFiles) {
                String blobID = Utils.sha1(Utils.readContents(file));
                File blobFile = Utils.join(BLOBS_DIR, blobID);

                try {
                    Files.copy(file.toPath(), blobFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Update newFileBlobs with the staged file
                newFileBlobs.put(file.getName(), blobID);
            }
        }

        // Remove files in the staging remove area from the new commit
        if (stagedRemoveFiles != null) {
            for (File file : stagedRemoveFiles) {
                newFileBlobs.remove(file.getName());
            }
        }

        // Create a new commit
        Commit newCommit = new Commit(message, c, g);
        newCommit.setFileBlobs(newFileBlobs);
        String newCommitID = newCommit.returnID();
        Utils.writeObject(Utils.join(COMMITS_DIR, newCommitID), newCommit);

        // Update the latest commit ID for the current branch
        // Remove old commit ID file in the branch directory
        File branchDir = join(LATEST_COMMITS, cBranch);
        for (File file : branchDir.listFiles()) {
            file.delete();
        }
        File newCommitFile = Utils.join(branchDir, newCommitID);
        Utils.writeObject(newCommitFile, newCommit);

        // Clear the staging area
        if (stagedAddFiles != null) {
            for (File file : stagedAddFiles) {
                file.delete();
            }
        }
        if (stagedRemoveFiles != null) {
            for (File file : stagedRemoveFiles) {
                file.delete();
            }
        }
    }

    private static void handleFilesInSplitPointButNotInGivenBranch(Commit s, Commit c, Commit g) {
        Map<String, String> splitBlobs = s.getFileBlobs();
        Map<String, String> currentBlobs = c.getFileBlobs();
        Map<String, String> givenBlobs = g.getFileBlobs();

        for (String fileName : splitBlobs.keySet()) {
            String splitBlob = splitBlobs.get(fileName);
            String currentBlob = currentBlobs.get(fileName);

            // If the file exists in the split point, not modified in the current branch, and does not exist in the given branch
            if (splitBlob != null && splitBlob.equals(currentBlob) && !givenBlobs.containsKey(fileName)) {
                // Delete the file and untrack it
                Utils.restrictedDelete(fileName);
                stageFileForRemoval(fileName);
            }
        }
    }

    private static void stageFileForRemoval(String fileName) {
        File file = Utils.join(REMOVE_STAGE, fileName);
        Utils.writeContents(file, fileName);
    }

    private static void handleFilesInGivenBranchOnlySinceSplitPoint(Commit s, Commit c, Commit g) {
        Map<String, String> splitBlobs = s.getFileBlobs();
        Map<String, String> currentBlobs = c.getFileBlobs();
        Map<String, String> givenBlobs = g.getFileBlobs();

        for (String fileName : givenBlobs.keySet()) {
            // If the file does not exist in the split point and the current branch, but only exists in the given branch
            if (!splitBlobs.containsKey(fileName) && !currentBlobs.containsKey(fileName)) {
                // Restore the file from the given branch and stage it
                checkoutFileFromCommit(g, fileName);
                stageFileForAddition(fileName);
            }
        }
    }

    private static void handleSameModifiedFilesInBothBranchSinceSplitPoint(Commit s, Commit c, Commit g) {
        Map<String, String> splitBlobs = s.getFileBlobs();
        Map<String, String> currentBlobs = c.getFileBlobs();
        Map<String, String> givenBlobs = g.getFileBlobs();

        for (String fileName : splitBlobs.keySet()) {
            String splitBlob = splitBlobs.get(fileName);
            String currentBlob = currentBlobs.get(fileName);
            String givenBlob = givenBlobs.get(fileName);

            // Check if the file is modified in the same way in both branches (same content or both deleted)
            if (currentBlob != null && currentBlob.equals(givenBlob)) {
                continue; // File has the same content in both branches, keep unchanged
            }

            if (currentBlob == null && givenBlob == null && !Utils.join(CWD, fileName).exists()) {
                continue; // File is deleted in both branches, and does not exist in the working directory, keep unchanged
            }
        }
    }

    private static void handleModifiedFilesInGivenButCurrentSinceSplitPoint(Commit s, Commit c, Commit g) {
        Map<String, String> splitBlobs = s.getFileBlobs();
        Map<String, String> currentBlobs = c.getFileBlobs();
        Map<String, String> givenBlobs = g.getFileBlobs();

        for (String fileName : givenBlobs.keySet()) {
            String splitBlob = splitBlobs.get(fileName);
            String currentBlob = currentBlobs.get(fileName);
            String givenBlob = givenBlobs.get(fileName);

            // If the file exists in the split point, not modified in the current branch, but modified in the given branch
            if (splitBlob != null && splitBlob.equals(currentBlob) && !splitBlob.equals(givenBlob)) {
                // Restore the file from the given branch and stage it
                checkoutFileFromCommit(g, fileName);
                stageFileForAddition(fileName);
            }
        }
    }

    private static void checkoutFileFromCommit(Commit commit, String fileName) {
        String blobID = commit.getFileBlobs().get(fileName);
        File blobFile = Utils.join(BLOBS_DIR, blobID);
        File targetFile = Utils.join(CWD, fileName);
        try {
            Files.copy(blobFile.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void stageFileForAddition(String fileName) {
        File file = Utils.join(CWD, fileName);
        File stagedFile = Utils.join(ADD_STAGE, fileName);
        try {
            Files.copy(file.toPath(), stagedFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Commit findSplitPoint(String currentBranch, String givenBranch) {
        // Get the latest commits of the current branch and the target branch
        Commit currentCommit = getLatestCommit(Utils.join(LATEST_COMMITS, currentBranch));
        Commit givenCommit = getLatestCommit(Utils.join(LATEST_COMMITS, givenBranch));

        // Collect IDs of all ancestors of the current branch
        Set<String> currentAncestors = new HashSet<>();
        Queue<Commit> queue = new LinkedList<>();
        queue.add(currentCommit);

        // Use BFS to traverse all ancestors of the current branch
        while (!queue.isEmpty()) {
            Commit commit = queue.poll();
            if (commit == null) {
                continue;
            }
            currentAncestors.add(commit.returnID());
            queue.add(commit.getParent());
            if (commit.isMerge()) {
                queue.add(commit.getMergeParent());
            }
        }

        // Use BFS to traverse all ancestors of the given branch
        queue.add(givenCommit);
        while (!queue.isEmpty()) {
            Commit commit = queue.poll();
            if (commit == null) {
                continue;
            }
            if (currentAncestors.contains(commit.returnID())) {
                return commit;
            }
            queue.add(commit.getParent());
            if (commit.isMerge()) {
                queue.add(commit.getMergeParent());
            }
        }

        return null; // Should not reach here if branches have a common ancestor
    }

}
