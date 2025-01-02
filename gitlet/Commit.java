package gitlet;

import java.io.Serializable;
import java.util.*;

import java.util.Date;

import static gitlet.Utils.sha1;

/**
 * Represents a gitlet commit object.
 * does at a high level.
 *
 * @author Zien Zeng
 */
public class Commit implements Serializable {
    /**
     * The message of this Commit.
     */
    private String message;
    private boolean isMerge;
    private Commit mergeParent;
    private Date timestamp;
    private Commit parent;
    private Map<String, String> fileBlobs;
    private String ID;

    public Commit(String message) {
        this.message = message;
        this.timestamp = new Date();
        this.parent = null;
        this.fileBlobs = new HashMap<>();
        this.isMerge = false;
        this.mergeParent = null;
        this.ID = getID();
    }

    public Commit(String message, Commit parent) {
        this.message = message;
        this.timestamp = new Date();
        this.parent = parent;
        this.fileBlobs = new HashMap<>();
        this.isMerge = false;
        this.mergeParent = null;
    }

    public Commit(String message, Commit parent, Commit mergeParent) {
        this.message = message;
        this.timestamp = new Date();
        this.parent = parent;
        this.fileBlobs = new HashMap<>();
        this.isMerge = true;
        this.mergeParent = mergeParent;
    }

    public String returnID() {
        return this.ID;
    }

    public String getID() {
        List<Object> vals = new ArrayList<>();
        vals.add(message);
        vals.add(timestamp.toString());
        vals.add(parent != null ? parent.getID() : "");
        String blobsString = fileBlobs.toString();
        vals.add(blobsString);
        vals.add(mergeParent != null ? mergeParent.getID() : "");
        return sha1(vals);
    }

    // Getter and setter methods
    public String getMessage() {
        return message;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public Commit getParent() {
        return parent;
    }

    public Map<String, String> getFileBlobs() {
        return fileBlobs;
    }

    public void setFileBlobs(Map<String, String> fileBlobs) {
        this.fileBlobs = fileBlobs;
        this.ID = getID();
    }

    public Commit getMergeParent() {
        return mergeParent;
    }

    public boolean isMerge() {
        return isMerge;
    }
}
