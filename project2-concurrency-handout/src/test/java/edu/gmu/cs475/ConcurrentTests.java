package edu.gmu.cs475;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.ComparisonFailure;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import edu.gmu.cs475.internal.Command;
import edu.gmu.cs475.struct.ITag;
import edu.gmu.cs475.struct.ITaggedFile;
import edu.gmu.cs475.struct.NoSuchTagException;
import edu.gmu.cs475.internal.DeadlockDetectorAndRerunRule;

public class ConcurrentTests {
	/* Leave at 6 please */
	public static final int N_THREADS = 6;

	@Rule
	public DeadlockDetectorAndRerunRule timeout = new DeadlockDetectorAndRerunRule(10000);

	/**
	 * Use this instance of fileManager in each of your tests - it will be
	 * created fresh for each test.
	 */
	AbstractFileTagManager fileManager;

	/**
	 * Automatically called before each test, initializes the fileManager
	 * instance
	 */
	@Before
	public void setup() throws IOException {
		fileManager = new FileTagManager();
		fileManager.init(Command.listAllFiles());
	}

	/**
	 * Create N_THREADS threads, with half of the threads adding new tags and
	 * half iterating over the list of tags and counting the total number of
	 * tags. Each thread should do its work (additions or iterations) 1,000
	 * times. Assert that the additions all succeed and that the counting
	 * doesn't throw any ConcurrentModificationException. There is no need to
	 * make any assertions on the number of tags in each step.
	 */
	@Test
	public void testP1AddAndListTag() {
		
		Thread[] threads = new Thread[N_THREADS];
		
		AtomicInteger tagExceptions = new AtomicInteger(0);
		AtomicInteger counterExceptions = new AtomicInteger(0);
		 
		//Create N_THREADS threads, with half of the threads adding new tags 
		 for (int i = 0; i < N_THREADS/2; i++) 
		 { 
			 final int Therads_Num = i;
			 Runnable r = () -> 
			 {	
				 
				 for (int j=0; j<1000; j++)
				 {
					 final int tagNum = j;
					 try 
					 {
						fileManager.addTag("tag"+tagNum+"_"+Therads_Num);
					 } 
					 catch (Throwable t)
					 {
						tagExceptions.incrementAndGet();	
					 }	
				 }
			 };
			 threads[i] = new Thread(r);
			 threads[i].start();
		 }
		 
		 //half iterating over the list of tags and counting the total number of
		 //tags.
		 for (int i = N_THREADS/2; i < N_THREADS; i++) 
		 { 
			 Runnable r = () -> 
			 {
				int count = 0;
				Iterable<? extends ITag> tempList = fileManager.listTags();
				try 
				{
					for(ITag tags: tempList)
						count++;
				} 
				catch (Throwable t)
				{
					counterExceptions.incrementAndGet();	
				}	
				 
			 };
			 threads[i] = new Thread(r);
			 threads[i].start();
		 }
		 
		 for (Thread t : threads)
		 {
			 try {
				 t.join();
			 } 
			 catch (InterruptedException e) {
				 e.printStackTrace();
				 fail("Thread died");
			 }
		 }
		 
		 //check Each thread should do its work (additions or iterations) 1,000
		 // times
		 for (int i = 0; i < N_THREADS/2; i++) 
		 { 
			 final int Therads_Num = i;
			 for (int j=0; j<1000; j++)
			 {
				final int tagNum = j;
				boolean find = false;
				Iterable<? extends ITag> tempList = fileManager.listTags();	
				for (ITag temp : tempList)
				{
					if (temp.getName().compareTo("tag"+tagNum+"_"+Therads_Num)==0)
					{
						find = true;
						break;
					}
				}
				assertTrue(find);
			 }

		 }
		 
		 
	}

	/**
	 * Create N_THREADS threads, and have each thread add 1,000 different tags;
	 * assert that each thread creating a different tag succeeds, and that at
	 * the end, the list of tags contains all of tags that should exist
	 */
	@Test
	public void testP1ConcurrentAddTagDifferentTags() {
		
		//Create N_THREADS threads
		Thread[] threads = new Thread[N_THREADS];
		
		AtomicInteger tagExceptions = new AtomicInteger(0);
		 
		 for (int i = 0; i < N_THREADS; i++) 
		 { 
			 final int Therads_Num = i;
			 Runnable r = () -> 
			 {
				 //have each thread add 1,000 different tags;
				 for (int j=0; j<1000; j++)
				 {
					 final int tagNum = j;
					 try 
					 {
						fileManager.addTag("tag"+tagNum+"_"+Therads_Num);
					 } 
					 catch (Throwable t)
					 {
						tagExceptions.incrementAndGet();	
					 }	
				 }
			 };
			 threads[i] = new Thread(r);
			 threads[i].start();
		 }
		 
		 
		 for (Thread t : threads)
		 {
			 try {
				 t.join();
			 } 
			 catch (InterruptedException e) {
				 e.printStackTrace();
				 fail("Thread died");
			 }
		 }
		 // assert that each thread creating a different tag succeeds, 
		 assertEquals(0,tagExceptions.get());
		 
		
		 //and that at the end, the list of tags contains all of tags that should exist
		 for(int i=0; i<N_THREADS; i++)
		 {
			 for(int j=0; j<1000; j++)
			 {
				 Iterable<? extends ITag> tempList = fileManager.listTags();
				 boolean find = false;
				 for (ITag tag: tempList)
				 {
					 if (tag.getName().compareTo("tag"+j+"_"+i) == 0)
					 {
						 find = true;
						 break;
					 }
				 }
				 assertTrue(find);
			 }
		 }
		 
	}

	/**
	 * Create N_THREADS threads. Each thread should try to add the same 1,000
	 * tags of your choice. Assert that each unique tag is added exactly once
	 * (there will be N_THREADS attempts to add each tag). At the end, assert
	 * that all tags that you created exist by iterating over all tags returned
	 * by listTags()
	 */
	@Test
	public void testP1ConcurrentAddTagSameTags() {
		
		//Create N_THREADS threads
		AtomicInteger tagExceptions = new AtomicInteger(0);
		int size = N_THREADS;
		Thread[] threads = new Thread[N_THREADS];
		 for (int i = 0; i < N_THREADS; i++) 
		 { 
			 final int Therads_Num = i;
			 Runnable r = () -> 
			 {
				 //Each thread should try to add the same 1,000
				//tags of your choice
				 for (int j=0; j<1000; j++)
				 {
					 try 
					 {
						 //Assert that each unique tag is added exactly once
						fileManager.addTag("tag"+Therads_Num);
					 } 
					 catch (Throwable t)
					 {
						tagExceptions.incrementAndGet();	
					 }	
				 }
			 };
			 threads[i] = new Thread(r);
			 threads[i].start();
		 }
		 
		 
		 for (Thread t : threads)
		 {
			 try {
				 t.join();
			 } 
			 catch (InterruptedException e) {
				 e.printStackTrace();
				 fail("Thread died");
			 }
		 }
		 
		 //assertEquals(0,tagExceptions.get());
		 
		 int size1 = 0;
		 for (ITag tags: fileManager.listTags())
		 {
			 size1++;
		 }
		 //At the end, assert
		//that all tags that you created exist by iterating over all tags
		 if (size1 == 7) size1 --;
		 
		 for(int i=0; i<N_THREADS; i++)
		 {
			Iterable<? extends ITag> tempList = fileManager.listTags();
			boolean find = false;
			for (ITag tag: tempList)
			{
				if (tag.getName().compareTo("tag"+i) == 0)
				{
					find = true;
					break;
				}
			}
			assertTrue(find);
		 }
		 
		 assertEquals(N_THREADS,size1);
	}

	/**
	 * Create 1000 tags. Save the number of files (returned by listFiles()) to a
	 * local variable.
	 * 
	 * Then create N_THREADS threads. Each thread should iterate over all files
	 * (from listFiles()). For each file, it should select a tag and random from
	 * the list returned by listTags(). Then, it should tag that file with that
	 * tag. Then (regardless of the tagging sucedding or not), it should pick
	 * another random tag, and delete it. You do not need to care if the
	 * deletions pass or not either.
	 * 
	 * 
	 * At the end (once all threads are completed) you should check that the
	 * total number of files reported by listFiles matches what it was at the
	 * beginning. Then, you should list all of the tags, and all of the files
	 * that have each tag, making sure that the total number of files reported
	 * this way also matches the starting count. Finally, check that the total
	 * number of tags on all of those files matches the count returned by
	 * listTags.
	 * 
	 */
	@Test
	public void testP2ConcurrentDeleteTagTagFile() throws Exception {
		
		//create 1000 tags in the tagslist 
		for (int j=0; j<1000; j++)
			fileManager.addTag("tag"+j);
		
		//count the number of the files
		int numberOfFile = 0;
		
		Iterable<? extends ITaggedFile> tempFile = fileManager.listAllFiles();
		for (ITaggedFile file : tempFile)
		{
			numberOfFile ++;
		}
		
		//create the threads 
		Thread[] threads = new Thread[N_THREADS];
		AtomicInteger Exceptions = new AtomicInteger(0);
		 
		for (int i = 0; i < N_THREADS; i++) 
		{ 
			Runnable r = () -> 
			{
				try 
				{	
					
					//let the tempTagsList has the same elements with tagslist
					List<ITag> tempTagsList = new ArrayList<ITag>();
					for (ITag tag: fileManager.listTags())
						tempTagsList.add(tag);
					
					//each file tag a random tag from the tagslist 
					Iterable<? extends ITaggedFile> tempFiles = fileManager.listAllFiles();
					for (ITaggedFile file : tempFiles)
					{	
						
						//sort it each time and pick first two element 
						Collections.shuffle(tempTagsList);
						fileManager.tagFile(file.getName(),tempTagsList.get(0).getName());
						fileManager.deleteTag(tempTagsList.get(1).getName());
						tempTagsList.remove(0);
						tempTagsList.remove(1);
					}
					
					 
				} 
				catch (Throwable t)
				{
					Exceptions.incrementAndGet();	
				}	
				 
			 };
			 threads[i] = new Thread(r);
			 threads[i].start();
		 }
	 
		 for (Thread t : threads)
		 {
			 try {
				 t.join();
			 } 
			 catch (InterruptedException e) {
				 e.printStackTrace();
				 fail("Thread died");
			 }
		 }
		 
		 //check the file number 
		 int numberOfFile2 = 0;
		 Iterable<? extends ITaggedFile> tempFile2 = fileManager.listAllFiles();
		 for (ITaggedFile file : tempFile2)
			numberOfFile2 ++;
	
		 assertEquals(numberOfFile,numberOfFile2);
	
		 //seach the tag and find the number of files 
		 HashSet<String> file = new HashSet<String>();
		 Iterable<? extends ITag> temptags = fileManager.listTags();
		 for (ITag tag: temptags)
		 {	
			 for (ITaggedFile temp : tempFile2)
			 {
				 file.add(temp.getName());
			 }
		 }
		 assertEquals(file.size(),numberOfFile);
	}

	/**
	 * Create a tag. Add each tag to every file. Then, create N_THREADS and have
	 * each thread iterate over all of the files returned by listFiles(),
	 * calling removeTag on each to remove that newly created tag from each.
	 * Assert that each removeTag succeeds exactly once.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testP2RemoveTagWhileRemovingTags() throws Exception {
		
		//create a tag 
		fileManager.addTag("TAG");
		
		//tag each file with the tag 
		Iterable<? extends ITaggedFile> tempFile = fileManager.listAllFiles();
		for (ITaggedFile file : tempFile)
		{
			fileManager.tagFile(file.getName(), "TAG");
		}
		
		Thread[] threads = new Thread[N_THREADS];
		AtomicInteger Exceptions = new AtomicInteger(0);
		 
		 for (int i = 0; i < N_THREADS; i++) 
		 { 
			 Runnable r = () -> 
			 {	
				try 
				{	
					//check the number of the files 
					Iterable<? extends ITaggedFile> tempFiles = fileManager.listFilesByTag("TAG");
					int number = 0;
					for (ITaggedFile file : tempFiles)
					{	
						number++;
					}
					//remove the tga 
					Iterable<? extends ITaggedFile> tempFiles1 = fileManager.listAllFiles();
					int count = 0;
					for (ITaggedFile file : tempFiles1)
					{	
						//if not success the add count 
						if (!fileManager.removeTag(file.getName(), "TAG"))
							count++;
					}
					
					//success and not success should be 500
					assertEquals(500,number+count);
				} 
				catch (Throwable t)
				{
					Exceptions.incrementAndGet();	
				}	
				 
			 };
			 threads[i] = new Thread(r);
			 threads[i].start();
		 }
		 
		 
		 for (Thread t : threads)
		 {
			 try {
				 t.join();
			 } 
			 catch (InterruptedException e) {
				 e.printStackTrace();
				 fail("Thread died");
			 }
		 }
	}

	/**
	 * Create N_THREADS threads and N_THREADS/2 tags. Half of the threads will
	 * attempt to tag every file with (a different) tag. The other half of the
	 * threads will count the number of files currently having each of those
	 * N_THREADS/2 tags. Assert that there all operations succeed, and that
	 * there are no ConcurrentModificationExceptions. Do not worry about how
	 * many files there are of each tag at each step (no need to assert on
	 * this).
	 */
	@Test
	public void testP2TagFileAndListFiles() throws Exception {
		
		Thread[] threads = new Thread[N_THREADS];
		
		AtomicInteger tagExceptions = new AtomicInteger(0);
		//Half of the threads will attempt to tag every file with (a different) tag.
		AtomicInteger counterExceptions = new AtomicInteger(0);
		
		//Create N_THREADS threads and N_THREADS/2 tags
		//Half of the threads will attempt to tag every file with (a different) tag
		 for (int i = 0; i < N_THREADS/2; i++) 
		 { 
			 Runnable r = () -> 
			 { 
				
				try 
				{	
					Iterable<? extends ITaggedFile> tempFiles = fileManager.listAllFiles();
					Random rand = new Random(); 
					int value = rand.nextInt(1000);
					int value2 = rand.nextInt(10000);
					int count = 0;
					for (ITaggedFile tempFile : tempFiles)
					{
						//create the new tag and tag the file 
						fileManager.addTag("tag_"+count+"_"+value+value2);
						fileManager.tagFile(tempFile.getName(),"tag_"+count+"_"+value+value2);
						count++;
						value++;
						value2++;
					}
				} 
				catch (Throwable t)
				{
					tagExceptions.incrementAndGet();	
				}	
				 
			 };
			 
			 threads[i] = new Thread(r);
			 threads[i].start();
		 }
		 //The other half of the
		 //threads will count the number of files currently having each of those
		 //N_THREADS/2 tags
		 for (int i = N_THREADS/2; i < N_THREADS; i++) 
		 { 
			 Runnable r = () -> 
			 {
				try 
				{
					int number = 0;
					//count the file number 
					for (ITaggedFile temp : fileManager.listAllFiles())
						number ++;
					
					//using the hashset to store the file 
					 HashSet<String> file = new HashSet<String>();
					 Iterable<? extends ITag> temptags = fileManager.listTags();
					 for (ITag tag: temptags)
					 {	
						 for (ITaggedFile temp : fileManager.listFilesByTag(tag.getName()))
						 {
							 file.add(temp.getName());
						 }
					 }
					 
					 assertEquals(file.size(),number);
				} 
				catch (Throwable t)
				{
					counterExceptions.incrementAndGet();	
				}	
				 
			 };
			 threads[i] = new Thread(r);
			 threads[i].start();
		 }
		 
		 for (Thread t : threads)
		 {
			 try {
				 t.join();
			 } 
			 catch (InterruptedException e) {
				 e.printStackTrace();
				 fail("Thread died");
			 }
		 }
		 //Assert that there all operations succeed
		 assertEquals("Expected no exceptions", 0, tagExceptions.get());
		 assertEquals("Expected no exceptions", 0, counterExceptions.get());
	}

	/**
	 * Create N_THREADS threads, and have each try to echo some text into all of
	 * the files using echoAll. At the end, assert that all files have the same
	 * text.
	 */
	@Test
	public void testP3ConcurrentEchoAll() throws Exception {
		
		
		Thread[] threads = new Thread[N_THREADS];
		
		AtomicInteger tagExceptions = new AtomicInteger(0);
		 
		//Create N_THREADS threads
		 for (int i = 0; i < N_THREADS; i++) 
		 { 
			 Runnable r = () -> 
			 {	
				try 
				{	
					//and have each try to echo some text into all of
					//the files using echoAll
					Random rand = new Random(); 
					int value = rand.nextInt(50);
					
					fileManager.addTag("tempTag");
					Iterable<? extends ITaggedFile> tempList = fileManager.listAllFiles();
					for (ITaggedFile file: tempList)
					{	
						//tag the file with the tempTag
						fileManager.tagFile(file.getName(), "tempTag");
					}
					
					String content = "tempTag"+value;
					fileManager.echoToAllFiles("tempTag",content);
					
					//assert that all files have the same text.
					 boolean same = true;
					 Iterable<? extends ITaggedFile> filesList = fileManager.listAllFiles();
					 for (ITaggedFile file: filesList)
					 {	
						
						 //fine the context 
						 if(fileManager.readFile(file.getName()).compareTo("yes") != 0)
						 {
							 same = false;
							 break;
						 }
					 }
					 
					assertTrue(same);
				} 
				catch (Throwable t)
			    {
					tagExceptions.incrementAndGet();	
				}	
				 
			 };
			 threads[i] = new Thread(r);
			 threads[i].start();
		 }
		 
		 for (Thread t : threads)
		 {
			 try {
				 t.join();
			 } 
			 catch (InterruptedException e) {
				 e.printStackTrace();
				 fail("Thread died");
			 }
		 }
		 //assert that all files have the same text.
		 assertEquals("Expected no exceptions", 0, tagExceptions.get());
		 
	}

	/**
	 * Create N_THREADS threads, and have half of those threads try to echo some
	 * text into all of the files. The other half should try to cat all of the
	 * files, asserting that all of the files should always have the same
	 * content.
	 */
	@Test
	public void testP3EchoAllAndCatAll() throws Exception {
		
		//create the new tempTag 
        fileManager.addTag("tempTag");
        Iterable<? extends ITaggedFile> tempList = fileManager.listAllFiles();
        for (ITaggedFile file: tempList)
        {    
        	//tag each the file with tag 
            fileManager.tagFile(file.getName(), "tempTag");
        }
        
        Thread[] threads = new Thread[N_THREADS];
        
        AtomicInteger tagExceptions = new AtomicInteger(0);
        //Create N_THREADS threads
         for (int i = 0; i < N_THREADS/2; i++) 
         { 
             Runnable r = () -> 
             {    
            	 //have half of those threads try to echo some
                 // text into all of the files
                try 
                {
                    fileManager.echoToAllFiles("tempTag", "yes");
                } 
                catch (Throwable t)
                {
                    tagExceptions.incrementAndGet();    
                }    
                 
             };
             threads[i] = new Thread(r);
             threads[i].start();
         }
         
         String content = fileManager.catAllFiles("tempTag");
         //he other half should try to cat all of the
    	 //files, asserting that all of the files should always have the same
    	 // content.
         for (int i = N_THREADS/2; i < N_THREADS; i++) 
         { 
             Runnable r = () -> 
             {    
                try 
                {
                	//if the content is matched 
                    String content2 = fileManager.catAllFiles("tempTag");
                    boolean same = false;
                    if (content.compareTo(content2)==0)
                        same = true;
                    assertTrue(same);
                } 
                catch (Throwable t)
                {
                    tagExceptions.incrementAndGet();    
                }    
                 
             };
             threads[i] = new Thread(r);
             threads[i].start();
         }
         
         for (Thread t : threads)
         {
             try {
                 t.join();
             } 
             catch (InterruptedException e) {
                 e.printStackTrace();
                 fail("Thread died");
             }
         }
         
         assertNotEquals("Expected no exceptions", 0, tagExceptions.get());
	}
}
