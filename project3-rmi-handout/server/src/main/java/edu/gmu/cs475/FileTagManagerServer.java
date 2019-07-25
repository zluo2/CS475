package edu.gmu.cs475;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;

import edu.gmu.cs475.internal.ServerMain;
import edu.gmu.cs475.struct.NoSuchTagException;
import edu.gmu.cs475.struct.TagExistsException;

public class FileTagManagerServer implements IFileTagManager {

	private final ScheduledThreadPoolExecutor timerExecutorService = new ScheduledThreadPoolExecutor(2);
	private static ScheduledFuture<?> syncThreadHandle = null;
	static ConcurrentMap<String,ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
	private LinkedList<Tag> tagsList;
	private LinkedList<TaggedFile> filesList;
	private StampedLock stampedlock = new StampedLock();
	
	public StampedLock getlock()
	{
		return stampedlock;
	}
	
	@Override
	public String readFile(String file) throws RemoteException, IOException {
		return new String(Files.readAllBytes(Paths.get(file)));
	}
	
	public FileTagManagerServer()
	{
		tagsList = new LinkedList<Tag>();	
		filesList = new LinkedList<TaggedFile>();
	}

	//TODO - implement all of the following methods:

	/**
	 * Initialize your FileTagManagerServer with files
	 * Each file should start off with the special "untagged" tag
	 * @param files
	 */
	public void init(List<Path> files) {
		//writelock 
		long stamp = stampedlock.writeLock();
		try{
			//using the iterator for the fileslist 
			ListIterator<Path> Iterator = files.listIterator();
			while(Iterator.hasNext())
			{
				//set path
				Path tempPath = Iterator.next();
				TaggedFile tempFile = new TaggedFile(tempPath);
				tempFile.tags.clear();
				//add the untagged
				Tag untagged = new Tag("untagged");
				tempFile.tags.add(untagged);
				filesList.addLast(tempFile);
			}
			
			Tag temptag = new Tag("untagged");
			tagsList.add(temptag);
		}
		finally
		{
			stampedlock.unlockWrite(stamp);
		}
	}

	/**
	 * List all currently known tags.
	 *
	 * @return List of tags (in any order)
	 */
	@Override
	public Iterable<String> listTags() throws RemoteException {
		
		//readlock
		long stamp = stampedlock.readLock();
		LinkedList<String> temptagsList = new LinkedList<String>();
		try{
			for (Tag tag: tagsList)
				temptagsList.addLast(tag.getName());
			return temptagsList;
		}
		finally
		{
			stampedlock.unlockRead(stamp);
		}
	}

	/**
	 * Add a new tag to the list of known tags
	 *
	 * @param name Name of tag
	 * @return The newly created Tag name
	 * @throws TagExistsException If a tag already exists with this name
	 */
	@Override
	public String addTag(String name) throws RemoteException, TagExistsException {
		//writelock 
		long stamp = stampedlock.writeLock();
		Tag newTag = null;
		try{
			//get the tagslist 
			ListIterator<Tag> Iterator = tagsList.listIterator();
			while(Iterator.hasNext())
			{	
				//match the name with tagslist to check exist or not
				Tag temp = Iterator.next();
				if (temp.getName().compareTo(name) == 0)
				{
					throw new TagExistsException();
				}	
			}
			//create the new tag and add it into tagslist
			newTag = new Tag(name);
			tagsList.addLast(newTag);
		}
		finally
		{
			stampedlock.unlockWrite(stamp);
		}
		return newTag.getName();
	}

	/**
	 * Update the name of a tag, also updating any references to that tag to
	 * point to the new one
	 *
	 * @param oldTagName Old name of tag
	 * @param newTagName New name of tag
	 * @return The newly updated Tag name
	 * @throws TagExistsException If a tag already exists with the newly requested name
	 * @throws NoSuchTagException If no tag exists with the old name
	 */
	@Override
	public String editTag(String oldTagName, String newTagName) throws RemoteException, TagExistsException, NoSuchTagException {
		//write lock 
		long stamp = stampedlock.writeLock();
		try{
			//get the tagslist 
			ListIterator<Tag> Iterator = tagsList.listIterator();
			while(Iterator.hasNext())
			{	
				//check the newtagname is exist or not
				Tag temp = Iterator.next();
				if (temp.getName().compareTo(newTagName) == 0)
				{
					throw new TagExistsException();
				}
			}
			
			ListIterator<Tag> Iterator2 = tagsList.listIterator();
			while(Iterator2.hasNext())
			{	
				//find the oldtag
				Tag temp = Iterator2.next();
				if (temp.getName().compareTo(oldTagName) == 0)
				{	
					//set the name to newtag
					temp.setName(newTagName);
					return temp.getName();
				}
			}
			//throw the exception
			throw new NoSuchTagException();
		}
		finally
		{
			stampedlock.unlockWrite(stamp);
		}
	}

	/**
	 * Delete a tag by name
	 *
	 * @param tagName Name of tag to delete
	 * @return The tag name that was deleted
	 * @throws NoSuchTagException         If no tag exists with that name
	 * @throws DirectoryNotEmptyException If tag currently has files still associated with it
	 */
	@Override
	public String deleteTag(String tagName) throws RemoteException, NoSuchTagException, DirectoryNotEmptyException {
		//writelock 
		long stamp = stampedlock.writeLock();
		Tag deleteTag = null;
		try{
			//check the tag is exist on the tagslist or not 
			ListIterator<Tag> Iterator = tagsList.listIterator();
			while(Iterator.hasNext())
			{
				Tag temp = Iterator.next();
				//find the tag and delete the it 
				if (temp.getName().compareTo(tagName) == 0)
				{
					if(!temp.files.isEmpty())
						throw new DirectoryNotEmptyException(" ");
					//remove the tag and save it for returning 
					Iterator.remove();
					deleteTag = temp;
				}
			}
	
			if (deleteTag == null)
				throw new NoSuchTagException();
		}
		finally
		{
			stampedlock.unlockWrite(stamp);
		}
		
		return deleteTag.getName();
	}

	/**
	 * List all files, regardless of their tag
	 *
	 * @return A list of all files. Each file must appear exactly once in this
	 * list.
	 */
	@Override
	public Iterable<String> listAllFiles() throws RemoteException {
		//readlock
		long stamp = stampedlock.readLock();
		LinkedList<String> tempFilesList = new LinkedList<String>();
		try{
			for (TaggedFile files: filesList)
				tempFilesList.addLast(files.getName());
			return tempFilesList;
		}
		finally
		{
			stampedlock.unlockRead(stamp);
		}
	}

	/**
	 * List all files that have a given tag
	 *
	 * @param tag Tag to look for
	 * @return A list of all files that have been labeled with the specified tag
	 * @throws NoSuchTagException If no tag exists with that name
	 */
	@Override
	public Iterable<String> listFilesByTag(String tag) throws RemoteException, NoSuchTagException {
		//define the readlock
		long stamp = stampedlock.readLock();
		
		try{
			//check the tag exit or not in tagslist 
			if (tag.compareTo("untagged")!=0){
				ListIterator<Tag> Iterator2 = tagsList.listIterator();
				Tag temptag = null;
				
				//using the iterator to match the tag 
				while(Iterator2.hasNext())
				{
					temptag = Iterator2.next();
					if (temptag.getName().compareTo(tag) == 0)
						break;
					else temptag = null;
				}
				//if not find, return exception
				if (temptag==null)
					throw new NoSuchTagException(); 
			}
			
			//search fileslist 
			
			LinkedList<String> tempFilesList = new LinkedList<String>();
			ListIterator<TaggedFile> Iterator = filesList.listIterator();
			while(Iterator.hasNext())
			{
				//search each tag and find the files then add it to a new list, return the list 
				TaggedFile tempFile = Iterator.next();
				for (Tag element : tempFile.tags)
					if (element.getName().compareTo(tag)==0)
						tempFilesList.addLast(tempFile.getName());
			}
			
			return tempFilesList;
		}
		finally
		{
			stampedlock.unlockRead(stamp);
		}
	}

	/**
	 * Label a file with a tag
	 * <p>
	 * Files can have any number of tags - this tag will simply be appended to
	 * the collection of tags that the file has. However, files can be tagged
	 * with a given tag exactly once: repeatedly tagging a file with the same
	 * tag should return "false" on subsequent calls.
	 * <p>
	 * If the file currently has the special tag "untagged" then that tag should
	 * be removed - otherwise, this tag is appended to the collection of tags on
	 * this file.
	 *
	 * @param file Path to file to tag
	 * @param tag  The desired tag
	 * @throws NoSuchFileException If no file exists with the given name/path
	 * @throws NoSuchTagException  If no tag exists with the given name
	 * @returns true if succeeding tagging the file, false if the file already
	 * has that tag
	 */
	@Override
	public boolean tagFile(String file, String tag) throws RemoteException, NoSuchFileException, NoSuchTagException {
		//writelock 
		long stamp = stampedlock.writeLock();
		try{
			//list the tagfile 
			ListIterator<TaggedFile> Iterator = filesList.listIterator();
			
			//using the tempFile to check file exist in filelist or not 
			TaggedFile tempFile = null;
			
			while(Iterator.hasNext())
			{
				tempFile = Iterator.next();
				if (tempFile.getName().compareTo(file) == 0)
					break;
				else tempFile = null;
			}
			//if tempFile is null, meaning that didn't find file 
			if (tempFile==null)
				throw new NoSuchFileException(" ");
			
			//check the tag exist or not 
			ListIterator<Tag> Iterator2 = tagsList.listIterator();
			Tag temptag = null;
			
			while(Iterator2.hasNext())
			{
				temptag = Iterator2.next();
				if (temptag.getName().compareTo(tag) == 0)
					break;
				else temptag = null;
			}
			//same method like checking file 
			if (temptag==null)
				throw new NoSuchTagException();
			
			boolean find = false;
			boolean untagged = false;
			
			//there two situation- tagged or untagged 
			for (Tag element : tempFile.tags)
			{
				if (element.getName().compareTo(tag) == 0)
					find = true;
				
				if (element.getName().compareTo("untagged") == 0)
					untagged = true;
			}
			
			//if untagged, then clear the tempfile tags 
			if(untagged)
			{
				tempFile.tags.clear();
			}
			
			//if not find on the file.tag, then tag the file and add the on taglist 
			if(find)
				return false;
			else 
			{
				tempFile.tags.add(new Tag(tag));
				temptag.files.add(tempFile);
				
				return true;
			}
		}
		finally
		{
			stampedlock.unlockWrite(stamp);
		}
	}

	/**
	 * Remove a tag from a file
	 * <p>
	 * If removing this tag causes the file to no longer have any tags, then the
	 * special "untagged" tag should be added.
	 * <p>
	 * The "untagged" tag can not be removed (return should be false)
	 *
	 * @param file Path to file to untag
	 * @param tag  The desired tag to remove from that file
	 * @throws NoSuchFileException If no file exists with the given name/path
	 * @throws NoSuchTagException  If no tag exists with the given name
	 * @returns True if the tag was successfully removed, false if there was no
	 * tag by that name on the specified file
	 */
	@Override
	public boolean removeTag(String file, String tag) throws RemoteException, NoSuchFileException, NoSuchTagException {
		//writelock 
		long stamp = stampedlock.writeLock();
        try{
            if (tag.compareTo("untagged")==0) 
                return false;
            
            ListIterator<TaggedFile> Iterator = filesList.listIterator();
            TaggedFile tempFile = null;
            
            while(Iterator.hasNext())
            {
                tempFile = Iterator.next();
                if (tempFile.getName().compareTo(file) == 0)
                    break;
                else tempFile = null;
            }
            
            if (tempFile==null)
                throw new NoSuchFileException(" ");
            
            ListIterator<Tag> Iterator2 = tagsList.listIterator();
            Tag temptag = null;
            
            while(Iterator2.hasNext())
            {
                temptag = Iterator2.next();
                if (temptag.getName().compareTo(tag) == 0)
                    break;
                else temptag = null;
            }
            
            if (temptag==null)
                throw new NoSuchTagException();
            
            boolean find = true;
            HashSet<Tag> temptags = new HashSet<>();
            for (Tag element : tempFile.tags)
            {    
                if (element.getName().compareTo(tag) == 0)
                {    
                    find = false;
                }
                else 
                    temptags.add(element);
            }
            
            if (find) 
                return false;
            else 
                tempFile.tags = temptags;
            
            
            if (tempFile.tags.isEmpty())
            {
                tempFile.tags.add(new Tag("untagged"));
            }
            
            return true;
        }
        finally
        {
            stampedlock.unlockWrite(stamp);
        }
	}

	/**
	 * List all of the tags that are applied to a file
	 *
	 * @param file The file to inspect
	 * @return A list of all tags that have been applied to that file in any
	 * order
	 * @throws NoSuchFileException If the file specified does not exist
	 */
	@Override
	public Iterable<String> getTags(String file) throws RemoteException, NoSuchFileException {
		//using readlock for concurrent 
		long stamp = stampedlock.readLock();
		//create a tempTaglist to store the tags
		LinkedList<String> tempTagList = new LinkedList<String>();
		try{
			//search filelist to check file 
			ListIterator<TaggedFile> Iterator = filesList.listIterator();
			TaggedFile tempFile = null;
			
			while(Iterator.hasNext())
			{
				tempFile = Iterator.next();
				if (tempFile.getName().compareTo(file) == 0)
					break;
				else tempFile = null;
			}
			
			if (tempFile==null)
				throw new NoSuchFileException(" ");
			
			//using for loop to add the tags into tempTaglist 
			for (Tag element : tempFile.tags)
			{
				tempTagList.add(element.getName());
			}
			
			return tempTagList;
		}
		finally
		{
			stampedlock.unlockRead(stamp);
		}
	}

	/**
	 * Acquires a read or write lock for a given file.
	 *
	 * @param name     File to lock
	 * @param forWrite True if a write lock is requested, else false
	 * @return A stamp representing the lock owner (e.g. from a StampedLock)
	 * @throws NoSuchFileException If the file doesn't exist
	 */
	@Override
	public long lockFile(String name, boolean forWrite) throws RemoteException, NoSuchFileException {
		long stamp = 0;
		//check the file is exist 
		ListIterator<TaggedFile> Iterator = filesList.listIterator();
		TaggedFile tempFile = null;
		
		while(Iterator.hasNext())
		{
			tempFile = Iterator.next();
			if (tempFile.getName().compareTo(name) == 0)
				break;
			else tempFile = null;
		}
		
		if (tempFile==null)
			throw new NoSuchFileException(" ");
		
		//if the file is for write then lock the file 
		if(forWrite)
		{
			if(tempFile.check.isEmpty())
				stamp = tempFile.lock.writeLock();
			else
			{
				tempFile.check.clear();
			}
		}
		else
		{
			stamp = tempFile.lock.tryReadLock();
		}
		
		tempFile.check.put(stamp, System.currentTimeMillis());
		System.out.println(stamp +" Time: "+System.currentTimeMillis());
		checkHeartBeat(name,stamp,forWrite);
		return stamp;
	}
	
	public void checkHeartBeat(String name,long stamp,boolean forWrite)
	{	
		futures.put(name, syncThreadHandle = timerExecutorService.scheduleAtFixedRate(new Runnable()
		{
            @Override
            public void run() {
            try {	
            	//find the file 
            	ListIterator<TaggedFile> Iterator = filesList.listIterator();
      			TaggedFile tempFile = null;
      		
      			while(Iterator.hasNext())
      			{
      				tempFile = Iterator.next();
      				if (tempFile.getName().compareTo(name) == 0)
      					break;
      				else tempFile = null;
      				
      			}
      			
      			if ((System.currentTimeMillis() - tempFile.check.get(stamp)) > 3000)
      			{
      				long temp = System.currentTimeMillis() - tempFile.check.get(stamp);
      				System.out.println(name);
      				System.out.println(System.currentTimeMillis());
      				System.out.println("different : " + temp);
      				unLockFile(name,stamp,forWrite);
      				System.out.println(" unlock NO."+stamp);
      				futures.get(name).cancel(true);
      				
      			}
            }
            catch (RemoteException | NoSuchFileException | IllegalMonitorStateException e)
            {
            	futures.get(name).cancel(true);
            }
            }
            	
        },
                100, 100, TimeUnit.MILLISECONDS
        ));
	}

	/**
	 * Releases a read or write lock for a given file.
	 *
	 * @param name     File to lock
	 * @param stamp    the Stamp representing the lock owner (returned from lockFile)
	 * @param forWrite True if a write lock is requested, else false
	 * @throws NoSuchFileException          If the file doesn't exist
	 * @throws IllegalMonitorStateException if the stamp specified is not (or is no longer) valid
	 */
	@Override
	public void unLockFile(String name, long stamp, boolean forWrite) throws RemoteException, NoSuchFileException, IllegalMonitorStateException {
		//check the file is exist or not 
		ListIterator<TaggedFile> Iterator = filesList.listIterator();
		TaggedFile tempFile = null;
		
		while(Iterator.hasNext())
		{
			tempFile = Iterator.next();
			if (tempFile.getName().compareTo(name) == 0)
				break;
			else tempFile = null;
		}
		
		if (tempFile==null)
			throw new NoSuchFileException(" ");
		
		if (!tempFile.check.containsKey(stamp))
			throw new IllegalMonitorStateException();
		
		if(forWrite)
		{
			if(!tempFile.getLock().isWriteLocked())
				throw new IllegalMonitorStateException();
		}
		else
		{
			if(!tempFile.getLock().isReadLocked())
				throw new IllegalMonitorStateException();
		}
		
		//if the file is forwrite then unlock 
		if(forWrite)
		{
			tempFile.getLock().unlockWrite(stamp);
			tempFile.check.remove(stamp);
		}
		else 
		{	
			tempFile.getLock().unlockRead(stamp);
			tempFile.check.remove(stamp);
		}
		
		System.out.println("unlock stamp NO."+stamp);
	}

	/**
	 * Notifies the server that the client is still alive and well, still using
	 * the lock specified by the stamp provided
	 *
	 * @param file    The filename (same exact name passed to lockFile) that we are
	 *                reporting in on
	 * @param stampId Stamp returned from lockFile that we are reporting in on
	 * @param isWrite if the heartbeat is for a write lock
	 * @throws IllegalMonitorStateException if the stamp specified is not (or is no longer) valid, or if
	 *                                      the stamp is not valid for the given read/write state
	 * @throws NoSuchFileException          if the file specified doesn't exist
	 */
	@Override
	public void heartbeat(String file, long stampId, boolean isWrite) throws RemoteException, IllegalMonitorStateException, NoSuchFileException {
		
		
	          		//check the file is exist or not 
	           		ListIterator<TaggedFile> Iterator = filesList.listIterator();
	           		TaggedFile tempFile = null;
	           		
	           		while(Iterator.hasNext())
	           		{
	           			tempFile = Iterator.next();
	           			if (tempFile.getName().compareTo(file) == 0)
	           				break;
	           			else tempFile = null;
	           		}
	           		
	           		if (tempFile==null)
	           			throw new NoSuchFileException(" ");
	           		
	           		if (!tempFile.check.containsKey(stampId))
	        			throw new IllegalMonitorStateException();
	           		
	           		//if the stamp specified is not (or is no longer) valid, or if
	           		//the stamp is not valid for the given read/write state
	           		if(isWrite)
	           		{
	           			if(!tempFile.getLock().isWriteLocked())
	           				throw new IllegalMonitorStateException();
	           		}
	           		else
	           		{
	           			if(!tempFile.getLock().isReadLocked())
	           				throw new IllegalMonitorStateException();
	           		}
	           		
	           		System.out.println("--------HeartBeat-----------");
	           		System.out.println(file);
	           		System.out.println(tempFile.check.get(stampId));
	           		tempFile.check.put(stampId, System.currentTimeMillis());
	           		System.out.println(tempFile.check.get(stampId));
	}

	/**
	 * Get a list of all of the files that are currently write locked
	 */
	@Override
	public List<String> getWriteLockedFiles() throws RemoteException {
		
		LinkedList<String> tempList = new LinkedList<String>();
		
		for (TaggedFile tempFile: filesList)
		{
			if(tempFile.getLock().isWriteLocked())
				tempList.add(tempFile.getName());
		}
		return tempList;
	}

	/**
	 * Get a list of all of the files that are currently write locked
	 */
	@Override
	public List<String> getReadLockedFiles() throws RemoteException {

		LinkedList<String> tempList = new LinkedList<String>();
		
		for (TaggedFile tempFile: filesList)
		{
			if(tempFile.getLock().isReadLocked())
				tempList.add(tempFile.getName());
		}
		return tempList;
	}

	@Override
	public void writeFile(String file, String content) throws RemoteException, IOException {
		Path path = Paths.get(file);
		if (!path.startsWith(ServerMain.BASEDIR))
			throw new IOException("Can only write to files in " + ServerMain.BASEDIR);
		Files.write(path, content.getBytes());

	}
}
