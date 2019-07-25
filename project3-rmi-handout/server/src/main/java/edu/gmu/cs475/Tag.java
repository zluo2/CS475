package edu.gmu.cs475;

import java.util.HashSet;
import java.util.concurrent.locks.StampedLock;

public class Tag{
	public HashSet<TaggedFile> files = new HashSet<>();
	private StampedLock lock = new StampedLock();

	private String name;

	public Tag(String name) {
		this.name = name;

	}

	public StampedLock getLock() {
		return lock;
	}

	public String getName() {
		return name;
	}

	public void setName(String newTagName) {
		this.name = newTagName;
	}
}
