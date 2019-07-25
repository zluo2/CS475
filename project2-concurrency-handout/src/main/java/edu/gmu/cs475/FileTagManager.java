package edu.gmu.cs475;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.locks.StampedLock;

import edu.gmu.cs475.struct.ITag;
import edu.gmu.cs475.struct.ITaggedFile;
import edu.gmu.cs475.struct.NoSuchTagException;
import edu.gmu.cs475.struct.TagExistsException;

public class FileTagManager extends AbstractFileTagManager {
	
	private LinkedList<Tag> tagsList;
	private LinkedList<TaggedFile> filesList;
	private StampedLock stampedlock = new StampedLock();
	
	public FileTagManager()
	{
		tagsList = new LinkedList<Tag>();
		filesList = new LinkedList<TaggedFile>();
	}
	
	/**
	 * List all currently known tags.
	 * 
	 * @return List of tags (in any order)
	 */
	@Override
	public Iterable<? extends ITag> listTags() {
		//readlock
		long stamp = stampedlock.readLock();
		LinkedList<Tag> temptagsList = new LinkedList<Tag>();
		try{
			for (Tag tag: tagsList)
				temptagsList.addLast(tag);
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
	 * @param name
	 *            Name of tag
	 * @return The newly created Tag object
	 * @throws TagExistsException
	 *             If a tag already exists with this name
	 */

	@Override
	public ITag addTag(String name) throws TagExistsException {
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
		return newTag;
	}
	
	/**
	 * Update the name of a tag, also updating any references to that tag to
	 * point to the new one
	 * 
	 * @param oldTagName
	 *            Old name of tag
	 * @param newTagName
	 *            New name of tag
	 * @return The newly updated Tag object
	 * @throws TagExistsException
	 *             If a tag already exists with the newly requested name
	 * @throws NoSuchTagException
	 *             If no tag exists with the old name
	 */

	@Override
	public ITag editTag(String oldTagName, String newTagName) throws TagExistsException, NoSuchTagException {
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
					return temp;
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
	 * @param tagName
	 *            Name of tag to delete
	 * @return The Tag object representing the tag that was deleted
	 * @throws NoSuchTagException
	 *             If no tag exists with that name
	 * @throws DirectoryNotEmptyException
	 *             If tag currently has files still associated with it
	 */
	@Override
	public ITag deleteTag(String tagName) throws NoSuchTagException, DirectoryNotEmptyException {
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
		
		return deleteTag;
	}
	
	/**
	 * Initialize your FileTagManager with the starting set of files. You do not
	 * need to persist tags on files from run to run. Each file should start
	 * with the default tag "untagged"
	 * 
	 * @param files
	 *            Starting list of files to consider
	 */
	@Override
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
		}
		finally
		{
			stampedlock.unlockWrite(stamp);
		}
	}
	
	/**
	 * List all files, regardless of their tag
	 * 
	 * @return A list of all files. Each file must appear exactly once in this
	 *         list.
	 * @throws NoSuchTagException
	 *             If no tag exists with that name
	 */

	@Override
	public Iterable<? extends ITaggedFile> listAllFiles() {
		//readlock 
		long stamp = stampedlock.readLock();
		try
		{	
			//return fileslist from the filemanager
			return filesList;
		}
		finally
		{
			stampedlock.unlockRead(stamp);
		}
	}
	
	/**
	 * List all files that have a given tag
	 * 
	 * @param tag
	 *            Tag to look for
	 * @return A list of all files that have been labeled with the specified tag
	 * @throws NoSuchTagException
	 *             If no tag exists with that name
	 */
	
	@Override
	public Iterable<? extends ITaggedFile> listFilesByTag(String tag) throws NoSuchTagException {
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
			
			LinkedList<TaggedFile> tempFilesList = new LinkedList<TaggedFile>();
			ListIterator<TaggedFile> Iterator = filesList.listIterator();
			while(Iterator.hasNext())
			{
				//search each tag and find the files then add it to a new list, return the list 
				TaggedFile tempFile = Iterator.next();
				for (Tag element : tempFile.tags)
					if (element.getName().compareTo(tag)==0)
						tempFilesList.addLast(tempFile);
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
	 * 
	 * Files can have any number of tags - this tag will simply be appended to
	 * the collection of tags that the file has. However, files can be tagged
	 * with a given tag exactly once: repeatedly tagging a file with the same
	 * tag should return "false" on subsequent calls.
	 * 
	 * If the file currently has the special tag "untagged" then that tag should
	 * be removed - otherwise, this tag is appended to the collection of tags on
	 * this file.
	 * 
	 * @param file
	 *            Path to file to tag
	 * @param tag
	 *            The desired tag
	 * @returns true if succeeding tagging the file, false if the file already
	 *          has that tag
	 * @throws NoSuchFileException
	 *             If no file exists with the given name/path
	 * @throws NoSuchTagException
	 *             If no tag exists with the given name
	 */
	
	@Override
	public boolean tagFile(String file, String tag) throws NoSuchFileException, NoSuchTagException {
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
	 * 
	 * If removing this tag causes the file to no longer have any tags, then the
	 * special "untagged" tag should be added.
	 * 
	 * The "untagged" tag can not be removed (return should be false)
	 * 
	 * @param file
	 *            Path to file to untag
	 * @param tag
	 *            The desired tag to remove from that file
	 * @returns True if the tag was successfully removed, false if there was no
	 *          tag by that name on the specified file
	 * @throws NoSuchFileException
	 *             If no file exists with the given name/path
	 * @throws NoSuchTagException
	 *             If no tag exists with the given name
	 */

	@Override
	public boolean removeTag(String file, String tag) throws NoSuchFileException, NoSuchTagException {
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
	 * @param file
	 *            The file to inspect
	 * @return A list of all tags that have been applied to that file in any
	 *         order
	 * @throws NoSuchFileException
	 *             If the file specified does not exist
	 */
	@Override
	public Iterable<? extends ITag> getTags(String file) throws NoSuchFileException {
		//using readlock for concurrent 
		long stamp = stampedlock.readLock();
		//create a tempTaglist to store the tags
		LinkedList<Tag> tempTagList = new LinkedList<Tag>();
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
				tempTagList.add(element);
			}
			
			return tempTagList;
		}
		finally
		{
			stampedlock.unlockRead(stamp);
		}
	}
	/**
	 * Return a file as a byte array.
	 * 
	 * @param file
	 *            Path to file requested
	 * @return String representing the file
	 * @throws IOException
	 *             if any IOException occurs in the underlying read
	 */
	@Override
	public String catAllFiles(String tag) throws NoSuchTagException, IOException {
		//using writelock for concurrent 
		long stamp = stampedlock.writeLock();
		String allFiles = "";
		try{
			//if tag is not untagged 
			if (tag.compareTo("untagged")!=0)
			{
				//check tag is exist on taglist or not 
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
			}
			
			//search filelist
			ListIterator<TaggedFile> Iterator = filesList.listIterator();
			
			while(Iterator.hasNext())
			{	
				TaggedFile tempFile = Iterator.next();
				//lock file for concurrent 
				long stamp1 = lockFile(tempFile.getName(), true);
				for(Tag element:tempFile.tags)
				{	
					//if this file has been tagged, then add it into allfiles 
					if (element.getName().compareTo(tag) == 0)
					{
						allFiles += readFile(tempFile.getName());
						break;
					}
				}
				//after unlockfile 
				unLockFile(tempFile.getName(),stamp1,false);
			}
		}
		finally
		{
			stampedlock.unlockWrite(stamp);
		}
		return allFiles;
	}
	/**
	 * Write (or overwrite) a file
	 *
	 * @param file
	 *            Path to file to write out
	 * @param content
	 *            String representing the content desired
	 * @throws IOException
	 *             if any IOException occurs in the underlying write
	 */
	@Override
	public void echoToAllFiles(String tag, String content) throws NoSuchTagException, IOException {
		//using writelock for concurrent 
		long stamp = stampedlock.writeLock();
		try{
			if (tag.compareTo("untagged")!=0)
			{
				//check the tag is exits or not 
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
			}
			
			//search the fileslist to match the tag
			ListIterator<TaggedFile> Iterator = filesList.listIterator();
			
			while(Iterator.hasNext())
			{
				TaggedFile tempFile = Iterator.next();
				//lock the file during working 
				long stamp1 = lockFile(tempFile.getName(),true);
				for(Tag element:tempFile.tags)
				{	
					//if match then add 
					if (element.getName().compareTo(tag) == 0)
					{
						writeFile(tempFile.getName(),content);
						break;
					}
				}
				//unlock the file 
				unLockFile(tempFile.getName(),stamp1,false);	
			}
		}
		finally
		{
			stampedlock.unlockWrite(stamp);
		}
		
	}
	/**
	 * Prints out all files that have a given tqg. Must internally synchronize
	 * to guarantee that the list of files with the given tag does not change
	 * during its call, and that each file printed does not change during its
	 * execution (using a read/write lock). You should acquire all of the locks,
	 * then read all of the files and release the locks. Your could should not
	 * deadlock while waiting to acquire locks.
	 * 
	 * @param tag
	 *            Tag to query for
	 * @return The concatenation of all of the files
	 * @throws NoSuchTagException
	 *             If no tag exists with the given name
	 * @throws IOException
	 *             if any IOException occurs in the underlying write
	 * 
	 */
	@Override
	public long lockFile(String name, boolean forWrite) throws NoSuchFileException {
		
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
			stamp = tempFile.lock.writeLock();
		else
			stamp = tempFile.lock.readLock();
		
		return stamp;
	}
	
	/**
	 * Releases a read or write lock for a given file.
	 * 
	 * @param name
	 *            File to lock
	 * @param stamp
	 *            the Stamp representing the lock owner (returned from lockFile)
	 * @param forWrite
	 *            True if a write lock is requested, else false
	 * @throws NoSuchFileException
	 *             If the file doesn't exist
	 */

	@Override
	public void unLockFile(String name, long stamp, boolean forWrite) throws NoSuchFileException {
		
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
		
		//if the file is forwrite then unlock 
		if(forWrite)
			tempFile.lock.unlockRead(stamp);
		else 
			tempFile.lock.unlockWrite(stamp);
		
	}

}
