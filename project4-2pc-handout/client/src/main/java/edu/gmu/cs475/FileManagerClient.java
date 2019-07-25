package edu.gmu.cs475;

import edu.gmu.cs475.struct.NoSuchTagException;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileManagerClient extends AbstractFileManagerClient {
	
	//file and content 
	private HashMap<String, String> Cache = new HashMap<>();
	private HashMap<Long, String> tempCacheFile = new HashMap<>();
	private HashMap<Long, String> tempCacheContent = new HashMap<>();
	
	//lock 
	private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
	private final Lock readLock = readWriteLock.readLock();
	private final Lock writeLock = readWriteLock.writeLock();

	public FileManagerClient(String host, int port) {
		super(host, port);
		startReplica();
	}

	/**
	 * Used for tests without a real server
	 *
	 * @param server
	 */
	public FileManagerClient(IFileManagerServer server) {
		super(server);
		startReplica();
	}

	/**
	 * Initialzes this read-only replica with the current set of files
	 *
	 * @param files A map from filename to file contents
	 */
	@Override
	protected void initReplica(HashMap<String, String> files) {
		writeLock.lock();
		try{
			//get the file, then put them into cache 
			for (Map.Entry<String,String> example : files.entrySet())
				Cache.put(example.getKey(), example.getValue());
		}finally{
			writeLock.unlock();
		}
	}

	/**
	 * Lists all of the paths to all of the files that are known to the client
	 *
	 * @return the file paths
	 */
	@Override
	public Iterable<String> listAllFiles() {
		readLock.lock();
		try{
			List<String> tempfileList = new ArrayList<>();
			//using fileList to store 
			for (String example: this.Cache.keySet())
				tempfileList.add(example);
			return tempfileList;
		}finally{
			readLock.unlock();
		}
	}

	/**
	 * Prints out all files. Must internally synchronize
	 * to guarantee that the list of files does not change
	 * during its call, and that each file printed does not change during its
	 * execution (using a read/write lock). You should acquire all of the locks,
	 * then read all of the files and release the locks. Your code should not
	 * deadlock while waiting to acquire locks.
	 *
	 * @return The concatenation of all of the files
	 * @throws NoSuchTagException If no tag exists with the given name
	 * @throws IOException        if any IOException occurs in the underlying read, or if the
	 *                            read was unsuccessful (e.g. if it times out, or gets
	 *                            otherwise disconnected during the execution
	 */
	@Override
	public String catAllFiles() throws NoSuchTagException, IOException {
		
		readLock.lock();
		try{
			String catAllFilesString = ""; 
	       
	        ConcurrentHashMap<String, Long> fileLock = new ConcurrentHashMap<>();  
	        // key - file, value - lock stamp
	        // sort the files return from fileList
	        List<String> Totalfiles = new ArrayList<>();
	        for(String fileName : Cache.keySet()) {
	            Totalfiles.add(fileName);
	        }
	        Collections.sort(Totalfiles);
	
	        try {
	            // acquire all of the locks for the Files
	            for(String tempfileName : Totalfiles) {
	                long stamp = lockFile(tempfileName, false);
	                fileLock.put(tempfileName, stamp);
	            }
	            // read all of the files to get the content 
	            for(String tempfileName : Totalfiles) {
	                catAllFilesString += readFile(tempfileName);
	            }       
	        } finally {
	            // release all of the locks
	            for(String fileName : Totalfiles) {
	                long stamp = fileLock.get(fileName);
	                unLockFile(fileName, stamp, false);
	                
	            }
	        }
	        return catAllFilesString;
		}finally{
			readLock.unlock();
		}
        
	}

	/**
	 * Echos some content into all files. Must internally
	 * synchronize to guarantee that the list of files does
	 * not change during its call, and that each file being printed to does not
	 * change during its execution (using a read/write lock)
	 * <p>
	 * Given two concurrent calls to echoToAllFiles, it will be indeterminate
	 * which call happens first and which happens last. But what you can (and
	 * must) guarantee is that all files will have the *same* value (and not
	 * some the result of the first, qnd some the result of the second). Your
	 * code should not deadlock while waiting to acquire locks.
	 * <p>
	 * Must use a transaction to guarantee that all writes succeed to all replicas (or none).
	 *
	 * @param content The content to write out to each file
	 * @throws NoSuchTagException If no tag exists with the given name
	 * @throws IOException        if any IOException occurs in the underlying write, or if the
	 *                            write was unsuccessful (e.g. if it times out, or gets
	 *                            otherwise disconnected during the execution)
	 */
	@Override
	public void echoToAllFiles(String content) throws IOException {
		
		writeLock.lock();
		try{
			// key  - file name, value - lock stamp 
	        ConcurrentHashMap<String, Long> fileLockMap = new ConcurrentHashMap<>();  
	
	        // sort the files return from files
	        List<String> Allfiles = new ArrayList<>();
	        for(String fileName : Cache.keySet()) {
	            Allfiles.add(fileName);
	        }
	        Collections.sort(Allfiles);
	        
	        long newID = 0;
	        boolean success = true;
	        try {
	            // acquire all of the locks
	            for(String fileName : Allfiles) {
	                long stamp = lockFile(fileName, true);
	                fileLockMap.put(fileName, stamp);
	            }
	            
	            newID = this.startNewTransaction();
	            // write content to all of the files
	            for(String fileName : Allfiles) {
	            	//begin the transcation from a new ID  
	            	if(!this.writeFileInTransaction(fileName, content, newID))
	            		success = false;
	            }
	        } catch(IOException ex){
	        	success = false;
	        }finally {
	        	//check the transaction 
	        	if (success)
	        	{
	        		this.issueCommitTransaction(newID);
	        	}
	        	else
	        	{
	        		this.issueAbortTransaction(newID);
	        	}
	        	
	            // release all of the locks
	            for(String fileName : Allfiles) {
	                long stamp = fileLockMap.get(fileName);
	                unLockFile(fileName, stamp, true);
	            }
	        }
		}finally{
			writeLock.unlock();
		}
	}

	/**
	 * Return a file as a byte array.
	 *
	 * @param file Path to file requested
	 * @return String representing the file
	 * @throws IOException if any IOException occurs in the underlying read
	 */
	@Override
	public String readFile(String file) throws RemoteException, IOException {
		readLock.lock();
		try{
			//if it doesn't have the file 
			if (!this.Cache.containsKey(file))
				throw new IOException();
			else 
				return this.Cache.get(file);
		}finally{
			readLock.unlock();
		}
	}

	/**
	 * Write (or overwrite) a file
	 *
	 * @param file    Path to file to write out
	 * @param content String representing the content desired
	 * @param xid     Transaction ID, if this write is associated with any transaction, or 0 if it is not associated with a transaction
	 *                If it is associated with a transaction, then this write must not be visible until the replicant receives a commit message for the associated transaction ID; if it is aborted, then it is discarded.
	 * @return true if the write was successful and we are voting to commit
	 * @throws IOException if any IOException occurs in the underlying write
	 */
	@Override
	public boolean innerWriteFile(String file, String content, long xid) throws RemoteException, IOException {
		
		writeLock.lock();
		try{
			boolean writeSucceed = false;
			if (xid == 0)
			{
				//if the xid == 0, not associated with any transaction, just put into cache 
				this.Cache.put(file, content);
				writeSucceed = true;
			}
			//get the repeat xid
			else if (tempCacheFile.containsKey(xid))
			{
				throw new IOException();
			}
			else
			{
				//assocaite with transaction, then put their into tempCache 
				this.tempCacheFile.put(xid,file);
				this.tempCacheContent.put(xid, content);
				writeSucceed = true;
			}
			
			return writeSucceed;
		}finally{
			writeLock.unlock();
		}
	}

	/**
	 * Commit a transaction, making any pending writes immediately visible
	 *
	 * @param id transaction id
	 * @throws RemoteException
	 * @throws IOException     if any IOException occurs in the underlying write
	 */
	@Override
	public void commitTransaction(long id) throws RemoteException, IOException {
		
		//using two temp 
		String file = null;
		String content = null;
		
		readLock.lock();
		try{
			
			//get the file and content from the tempCache 
			if(tempCacheFile.containsKey(id))
			{
				file = tempCacheFile.get(id);
				content = tempCacheContent.get(id);
			}
			else 
			{
				throw new IOException();
			}
		}finally{
			readLock.unlock();
		}
		
		writeLock.lock();
		try{
			tempCacheFile.remove(id);
			tempCacheContent.remove(id);
			//store the file and content into cache 
			this.Cache.put(file, content);
		}finally{
			writeLock.unlock();
		}
	}

	/**
	 * Abort a transaction, discarding any pending writes that are associated with it
	 *
	 * @param id transaction id
	 * @throws RemoteException
	 */
	@Override
	public void abortTransaction(long id) throws RemoteException {
		
		writeLock.lock();
		try{
			//delete the file and content from the tempCache
			tempCacheFile.remove(id);
			tempCacheContent.remove(id);
		}finally{
			writeLock.unlock();
		}
		
	}
}

