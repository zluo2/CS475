package edu.gmu.cs475;

import edu.gmu.cs475.struct.NoSuchTagException;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;

public class FileTagManagerClient extends AbstractFileTagManagerClient {

	public FileTagManagerClient(String host, int port) {
		super(host, port);
	}

	/**
	 * Used for tests without a real server
	 * 
	 * @param server
	 */
	public FileTagManagerClient(IFileTagManager server) {
		super(server);
	}

	/* It is strongly suggested that you use the timerExecutorService to manage your timers, but not required*/
	//https://stackoverflow.com/questions/9334269/how-to-shutdown-only-selected-thread-in-scheduledexecutorservice
	private final ScheduledThreadPoolExecutor timerExecutorService = new ScheduledThreadPoolExecutor(2);
	private static ScheduledFuture<?> syncThreadHandle = null;
	static ConcurrentMap<Long,ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
	private StampedLock stampedlock = new StampedLock();

	//TODO - implement the following methods; the rest are automatically stubbed out to the server

	/**
	 * Prints out all files that have a given tqg. Must internally synchronize
	 * to guarantee that the list of files with the given tag does not change
	 * during its call, and that each file printed does not change during its
	 * execution (using a read/write lock). You should acquire all of the locks,
	 * then read all of the files and release the locks. Your could should not
	 * deadlock while waiting to acquire locks.
	 *
	 * @param tag Tag to query for
	 * @return The concatenation of all of the files
	 * @throws NoSuchTagException If no tag exists with the given name
	 * @throws IOException        if any IOException occurs in the underlying read, or if the
	 *                            read was unsuccessful (e.g. if it times out, or gets
	 *                            otherwise disconnected during the execution
	 */
	@Override
	public String catAllFiles(String tag) throws NoSuchTagException, IOException {
		//using writelock for concurrent 
		long stamp = this.stampedlock.writeLock();
		String allFiles = "";
		try{
			//if tag is not untagged 
			String temptag = "untagged";
			if (tag.compareTo("untagged")!=0)
			{
				//check tag is exist on taglist or not 
				Iterator<String> Iterator2 = this.listTags().iterator();
				
				while(Iterator2.hasNext())
				{
					temptag = Iterator2.next();
					if (temptag.compareTo(tag) == 0)
						break;
					else temptag = null;
				}
				
				if (temptag==null)
					throw new NoSuchTagException();
			}
			
			//search filelist
			Iterable<String> files = this.listFilesByTag(temptag);
			ArrayList<Long> lock = new ArrayList<Long>();
			
			for (String tempfile: files)
			{	
				long stamp1 = lockFile(tempfile, false);
				lock.add(stamp1);
			}
			
			for (String tempfile:files)
			{	
				long stamp1 = lock.remove(0);
				unLockFile(tempfile,stamp1,false);
				allFiles += this.readFile(tempfile);
			}
		}
		finally
		{
			stampedlock.unlockWrite(stamp);
		}
		return allFiles;
	}

	/**
	 * Echos some content into all files that have a given tag. Must internally
	 * synchronize to guarantee that the list of files with the given tag does
	 * not change during its call, and that each file being printed to does not
	 * change during its execution (using a read/write lock)
	 * <p>
	 * Given two concurrent calls to echoToAllFiles, it will be indeterminate
	 * which call happens first and which happens last. But what you can (and
	 * must) guarantee is that all files will have the *same* value (and not
	 * some the result of the first, qnd some the result of the second). Your
	 * could should not deadlock while waiting to acquire locks.
	 *
	 * @param tag     Tag to query for
	 * @param content The content to write out to each file
	 * @throws NoSuchTagException If no tag exists with the given name
	 * @throws IOException        if any IOException occurs in the underlying write, or if the
	 *                            write was unsuccessful (e.g. if it times out, or gets
	 *                            otherwise disconnected during the execution)
	 */
	@Override
	public void echoToAllFiles(String tag, String content) throws NoSuchTagException, IOException {
		//using writelock for concurrent 
		long stamp = stampedlock.writeLock();
		try{
			String temptag = "untagged";
			if (tag.compareTo("untagged")!=0)
			{
				//check the tag is exits or not 
				Iterator<String> Iterator2 = this.listTags().iterator();
				
				
				while(Iterator2.hasNext())
				{
					temptag = Iterator2.next();
					if (temptag.compareTo(tag) == 0)
						break;
					else temptag = null;
				}
				
				if (temptag==null)
					throw new NoSuchTagException();
			}
			
			//search the fileslist to match the tag
			
			Iterable<String> files = this.listFilesByTag(temptag);
			ArrayList<Long> lock = new ArrayList<Long>();
			
			for (String tempfile: files)
			{
				long stamp1 = lockFile(tempfile, true);
				lock.add(stamp1);
			}
			
			for (String tempfile:files)
			{	
				long stamp1 = lock.remove(0);
				unLockFile(tempfile,stamp1,true);
				writeFile(tempfile,content);
			}
		}
		finally
		{
			stampedlock.unlockWrite(stamp);
		}
	}

	/**
	 * A callback for you to implement to let you know that a lock was
	 * successfully acquired, and that you should start sending heartbeats for
	 * it
	 *
	 * @param name
	 * @param forWrite
	 * @param stamp
	 */
	@Override
	public void lockFileSuccess(String name, boolean forWrite, long stamp) {
		
		futures.put(stamp, syncThreadHandle = timerExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
               try {
            	   heartbeat(name,stamp,forWrite);
               } catch (NoSuchFileException | RemoteException | IllegalMonitorStateException e) {
            	   futures.get(stamp).cancel(true);
               }
            }
        },
                2, 2, TimeUnit.SECONDS
        ));
	}

	/**
	 * A callback for you to implement to let you know a lock was relinquished,
	 * and that you should stop sending heartbeats for it.
	 *
	 * @param name
	 * @param forWrite
	 * @param stamp
	 */
	@Override
	public void unLockFileCalled(String name, boolean forWrite, long stamp) {
		
		futures.get(stamp).cancel(true);
	}
}
