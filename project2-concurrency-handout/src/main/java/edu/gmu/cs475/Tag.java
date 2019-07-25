package edu.gmu.cs475;

import java.util.HashSet;
import java.util.concurrent.locks.StampedLock;

import edu.gmu.cs475.struct.ITag;

public class Tag implements ITag {
	public HashSet<TaggedFile> files = new HashSet<>();
	private StampedLock lock = new StampedLock();

	private String name;

	public Tag(String name) {
		this.name = name;

	}

	public StampedLock getLock() {
		return lock;
	}

	@Override
	public String getName() {
		return name;
	}

	public void setName(String newTagName) {
		this.name = newTagName;
	}
}
