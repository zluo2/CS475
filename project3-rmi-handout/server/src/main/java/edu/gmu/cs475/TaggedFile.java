package edu.gmu.cs475;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.locks.StampedLock;

public class TaggedFile{
	public HashSet<Tag> tags = new HashSet<>();
	public StampedLock lock = new StampedLock();
	public HashMap<Long, Long> check = new HashMap<Long, Long>();
	
	public StampedLock getLock() {
		return lock;
	}
	
	private Path path;
	public TaggedFile(Path path)
	{
		this.path = path;
	}
	

	public String getName() {
		return path.toString();
	}

	public String toString() {
		return getName();
	}
}
