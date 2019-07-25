package edu.gmu.cs475;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.concurrent.locks.StampedLock;


public class TaggedFile {
    private Path path;
    private StampedLock lock = new StampedLock();
    
    public StampedLock getLock() {
        return lock;
    }
    
    public TaggedFile(Path path) {
        this.path = path;
    }

    public String getName() {
        long stamp = lock.readLock();
        try {
            return path.toString();
        } finally {
            lock.unlockRead(stamp);
        }
    }
    @Override
    public String toString() {
        return getName();
    }

    @Override
    public boolean equals(Object other) {
        long stamp = lock.readLock();
        try {
            if(other instanceof TaggedFile) {
                return this.path.equals(((TaggedFile) other).path);
            }
        } finally {
            lock.unlockRead(stamp);
        }
        return false;
    }
}
