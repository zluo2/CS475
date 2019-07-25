package edu.gmu.cs475;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileManagerServer extends AbstractFileManagerServer {
	
    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();
    
    private List<TaggedFile> files = new ArrayList<>();
    private HashMap<Integer, IFileReplica> replicas = new HashMap<>();
    
    Random rand = new Random();  
    private AtomicLong count = new AtomicLong();;
    
	/**
	 * Initializes the server with the state of the files it cares about
	 *
	 * @param files list of the files
	 */
	public void init(List<Path> files) {
		//get the writelock 
		writeLock.lock();
		//add each file into the files 
		try
		{
			for(int i = 0; i < files.size(); i++) {
				//create the new file, then add it into files
                TaggedFile file = new TaggedFile(files.get(i));
                this.files.add(file);
            }
		}
		finally
		{
			writeLock.unlock();
		}
	}

	/**
	 * Registers a replica with the server, returning all of the files that currently exist.
	 *
	 * @param hostname   the hostname of the replica talking to you (passed again at disconnect)
	 * @param portNumber the port number of the replica talking to you (passed again at disconnect)
	 * @param replica    The RMI object to use to signal to the replica
	 * @return A HashMap of all of the files that currently exist, mapping from filepath/name to its contents
	 * @throws IOException in case of an underlying IOExceptino when reading the files
	 */
	@Override
	public HashMap<String, String> registerReplica(String hostname, int portNumber, IFileReplica replica) throws IOException {
		
		//using the writelock to protect 
		writeLock.lock();
        try {
        	//resiger the replicas with the server.
            replicas.put(portNumber, replica);
        } finally {
            writeLock.unlock();
        }
        
        //returning all of the files 
        //using a tempFile to store all the file 
        HashMap<String, String> tempfiles = new HashMap<>();
        readLock.lock();
        try {
            for(TaggedFile file: this.files) {
            	//get the file name and content 
                String Name = file.getName();
                String content = readFileLocally(Name);
                tempfiles.put(Name, content);
            }
            return tempfiles;
        } finally {
            readLock.unlock();
        }
	}

	/**
	 * Write (or overwrite) a file.
	 * You must not allow a client to register or depart during a write.
	 *
	 * @param file    Path to file to write out
	 * @param content String representing the content desired
	 * @throws IOException if any IOException occurs in the underlying write, OR if the write is not succesfully replicated
	 */
	@Override
	public void writeFile(String file, String content) throws RemoteException, IOException {
		//get the stamp from the lockedFile
		long stamp = lockFile(file, true);
		//using the readlock 
        readLock.lock();
        try {
        	//generate the transcation ID from the count 
        	long xID = this.startNewTransaction();
        	
        	//check the every innerWrite is ok 
        	boolean allOK = true;
        	
        	// get the replica from the replica
            for(IFileReplica replica : replicas.values()) {
            	
            	try{
            		//get the false from the innerwrite 
            		if (replica.innerWriteFile(file, content, xID) == false)
            			allOK = false;
            	}
            	catch(IOException ex){
            		//if innerwrite throw a IOException
            		allOK = false;
            	}
            }
            
            if (allOK)
            {
            	//write the file locally
            	writeFileLocally(file, content);
            	//write into each client
            	for(IFileReplica replica : replicas.values()) {
            		replica.commitTransaction(xID);
            	}
            }
            else 
            {	
            	//call abort for each client
            	for(IFileReplica replica : replicas.values()) {
            		replica.abortTransaction(xID);
            	}
            	throw new IOException();
            }
            	
        } finally {
            unLockFile(file, stamp, true);
            readLock.unlock();
        }
	}

	/**
	 * Write (or overwrite) a file. Broadcasts the write to all replicas and locally on the server
	 * You must not allow a client to register or depart during a write.
	 *
	 * @param file    Path to file to write out
	 * @param content String representing the content desired
	 * @param xid     Transaction ID to use for any replications of this write
	 * @return True if all replicas replied "OK" to this write
	 * @throws IOException if any IOException occurs in the underlying write, OR if the write is not succesfully replicated
	 */
	@Override
	public boolean writeFileInTransaction(String file, String content, long xid) throws RemoteException, IOException {
		readLock.lock();
		boolean allOK = true;
		try{
			
			// get the replica from the replica
	        for(IFileReplica replica : replicas.values()) {
	        	
	        	try{
	        		//get the false from the innerwrite 
	        		if (replica.innerWriteFile(file, content, xid) == false)
	        			allOK = false;
	        			
	        	}
	        	catch(IOException ex){
	        		//if innerwrite throw a IOException
	        		allOK = false;
	        	}
	        }
	        
	        if(allOK)
	        	writeFileLocally(file, content);
	        
	        return allOK;
		}finally{
			readLock.unlock();
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
		
        TaggedFile tempfile = null;
        readLock.lock();
        try {
        	TaggedFile tempFile1 = new TaggedFile(new File(name).toPath());
            int index = files.indexOf(tempFile1);
            //doesn't exit the file 
            if(index == -1) {
                throw new NoSuchFileException(" ");
            }
            // get the tempFile from fileList using its index
            tempfile = files.get(index);
        } finally {
            readLock.unlock();
        }

        long stamp = 0;
        // True - write false - read 
        if(forWrite) {
            stamp = tempfile.getLock().writeLock(); 
        } else {
            stamp = tempfile.getLock().readLock();
        }
        return stamp;
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
		
		TaggedFile file = null;
        readLock.lock();
        try {
        	//using the index to identify NoSuchFilException. 
        	TaggedFile tempFile1 = new TaggedFile(new File(name).toPath());
            int index = files.indexOf(tempFile1);
            //doesn't exit the file
            if(index == -1) {
                throw new NoSuchFileException(" ");
            }
            // get the File from fileList using its index
            file = files.get(index);
        } finally {
            readLock.unlock();
        }
        // True - write false - read 
        if(forWrite) {
            file.getLock().unlockWrite(stamp);
        } else {
            file.getLock().unlockRead(stamp);
        }
	}

	/**
	 * Notifies the server that a cache client is shutting down (and hence no longer will be involved in writes)
	 *
	 * @param hostname   The hostname of the client that is disconnecting (same hostname specified when it registered)
	 * @param portNumber The port number of the client that is disconnecting (same port number specified when it registered)
	 * @throws RemoteException
	 */
	@Override
	public void cacheDisconnect(String hostname, int portNumber) throws RemoteException {
		//using the writeLock to protect 
		writeLock.lock();
        try {
        	//remove this replicas from the replicas
            replicas.remove(portNumber);
        } finally {
            writeLock.unlock();
        }
	}

	/**
	 * Request a new transaction ID to represent a new, client-managed transaction
	 *
	 * @return Transaction organizer-provided ID that will be used in the future to commit or abort this transaction
	 */
	@Override
	public long startNewTransaction() throws RemoteException {
		
		//get the newID
			long newID = count.incrementAndGet();
			return newID;
		
	}

	/**
	 * Broadcast to all replicas (and make updates locally as necessary on the server) that a transaction should be committed
	 * You must not allow a client to register or depart during a commit.
	 *
	 * @param xid transaction ID to be committed (from startNewTransaction)
	 * @throws IOException in case of any underlying IOException upon commit
	 */
	@Override
	public void issueCommitTransaction(long xid) throws RemoteException, IOException {
		readLock.lock();
		try{
			//write into each client
	    	for(IFileReplica replica : replicas.values()) {
	    		replica.commitTransaction(xid);
	    	}
		}finally{
			readLock.unlock();
		}
	}

	/**
	 * Broadcast to all replicas (and make updates locally as necessary on the server) that a transaction should be aborted
	 * You must not allow a client to register or depart during an abort.
	 *
	 * @param xid transaction ID to be committed (from startNewTransaction)
	 */
	@Override
	public void issueAbortTransaction(long xid) throws RemoteException {
		readLock.lock();
		try{
			//transaction should be aborted
			for(IFileReplica replica : replicas.values()) {
	    		replica.abortTransaction(xid);
	    	}
		}finally{
			readLock.unlock();
		}
	}
}
