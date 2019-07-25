package edu.gmu.cs475;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import edu.gmu.cs475.internal.Command;
import edu.gmu.cs475.struct.ITag;
import edu.gmu.cs475.struct.ITaggedFile;
import edu.gmu.cs475.struct.NoSuchTagException;
import edu.gmu.cs475.struct.TagExistsException;

public class NonConcurrentTests {
	AbstractFileTagManager fileManager;
	
	@Rule
	public Timeout globalTimeout = new Timeout(8000);
	
	@Before
	public void setup() {
		fileManager = new FileTagManager();
	}

	@Test
	public void testP1AddTag() throws Exception {
		fileManager.addTag("foo");
		Iterable<? extends ITag> res = fileManager.listTags();
		boolean foundFoo = false;
		for (ITag t : res) {
			if (t.getName().equals("foo")) {
				foundFoo = true;
			} else if (!t.getName().equals("untagged")) {
				fail("Unexpected tag found: " + t);
			}
		}
		assertTrue("Created tag exists", foundFoo);
	}

	@Test
	public void testP1AddTagAlreadyExists() throws Exception {
		fileManager.addTag("foo");
		boolean exception = false;
		try {
			fileManager.addTag("foo");
		} catch (TagExistsException ex) {
			exception = true;
		}
		assertTrue("Expected an exception to be thrown", exception);
	}

	@Test
	public void testP1EditTag() throws Exception {
		fileManager.addTag("foo");
		fileManager.editTag("foo", "bar");
		Iterable<? extends ITag> res = fileManager.listTags();
		ITag first = res.iterator().next();
		assertEquals("bar", first.getName());
		boolean foundBar = false;

		for (ITag t : res) {
			if (t.getName().equals("bar")) {
				foundBar = true;
			} else if (!t.getName().equals("untagged")) {
				fail("Unexpected tag found: " + t);
			}
		}
		assertTrue("Created tag exists", foundBar);

	}

	@Test(expected = NoSuchTagException.class)
	public void testP1EditNonExistantTag() throws Exception {
		fileManager.editTag("foo", "bar");
	}

	@Test
	public void testP1EditTagAlreadyExists() throws Exception {
		fileManager.addTag("foo");
		fileManager.addTag("bar");
		boolean exception = false;
		try {
			fileManager.editTag("foo", "bar");
		} catch (TagExistsException ex) {
			exception = true;
		}
		assertTrue("Expected an exception to be thrown", exception);
	}

	@Test
	public void testP1DeleteTag() throws Exception {
		fileManager.addTag("foo");
		fileManager.deleteTag("foo");
		Iterable<? extends ITag> res = fileManager.listTags();
		Iterator<? extends ITag> iterator = res.iterator();
		assertFalse(iterator.hasNext());
	}

	@Test(expected = NoSuchTagException.class)
	public void testP1DeleteTagDoesntExist() throws Exception {
		fileManager.deleteTag("foo");
	}

	@Test
	public void testP1DeleteTagNotEmpty() throws Exception {
		fileManager.init(Collections.singletonList(Paths.get("fooFile")));
		fileManager.addTag("foo");
		fileManager.tagFile("fooFile", "foo");
		boolean caught = false;
		try {
			fileManager.deleteTag("foo");
		} catch (DirectoryNotEmptyException ex) {
			caught = true;
		}
		boolean found =false;
		for(ITag t: fileManager.listTags())
			if(t.getName().equals("foo"))
				found =true;
		assertTrue("DirectoryNotEmptyException expected",caught);
		assertTrue("Not empty tag should not have been deleted",found);
	}

	@Test
	public void testP2Init() throws Exception {
		fileManager.init(Collections.singletonList(Paths.get("fooFile")));
		Iterable<? extends ITaggedFile> files = fileManager.listFilesByTag("untagged");
		Iterator<? extends ITaggedFile> iter = files.iterator();
		ITaggedFile file = iter.next();
		assertEquals(file.getName(), "fooFile");
		assertFalse(iter.hasNext());

		Iterator<? extends ITag> tags = fileManager.getTags("fooFile").iterator();
		assertEquals("untagged", tags.next().getName());
		assertFalse(tags.hasNext());
	}

	@Test
	public void testP2TagFile() throws Exception {
		fileManager.init(Collections.singletonList(Paths.get("fooFile")));
		fileManager.addTag("foo");
		fileManager.tagFile("fooFile", "foo");
		Iterator<? extends ITag> tags = fileManager.getTags("fooFile").iterator();
		assertEquals("foo", tags.next().getName());
		assertFalse(tags.hasNext());

		Iterable<? extends ITaggedFile> files = fileManager.listFilesByTag("foo");
		Iterator<? extends ITaggedFile> iter = files.iterator();
		ITaggedFile file = iter.next();
		assertEquals(file.getName(), "fooFile");
		assertFalse(iter.hasNext());

	}

	@Test
	public void testP2TagFileAlreadyExists() throws Exception {
		fileManager.init(Collections.singletonList(Paths.get("fooFile")));
		fileManager.addTag("foo");
		fileManager.tagFile("fooFile", "foo");
		assertFalse(fileManager.tagFile("fooFile", "foo"));
	}

	@Test(expected = NoSuchTagException.class)
	public void testP2TagFileNoSuchTag() throws Exception {
		fileManager.init(Collections.singletonList(Paths.get("fooFile")));
		fileManager.tagFile("fooFile", "foo");
	}

	@Test
	public void testP2RemoveTag() throws Exception {
		fileManager.init(Collections.singletonList(Paths.get("fooFile")));
		fileManager.addTag("foo");
		fileManager.tagFile("fooFile", "foo");
		fileManager.removeTag("fooFile", "foo");

		Iterator<? extends ITag> tags = fileManager.getTags("fooFile").iterator();
		assertEquals("untagged", tags.next().getName());
		assertFalse(tags.hasNext());
	}

	@Test
	public void testP2RemoveUntagged() throws Exception {
		fileManager.init(Collections.singletonList(Paths.get("fooFile")));
		assertFalse("Removing the untagged tag should be impossible", fileManager.removeTag("fooFile", "untagged"));
	}

	@Test(expected = NoSuchTagException.class)
	public void testP2RemoveTagDoesntExist() throws Exception {
		fileManager.init(Collections.singletonList(Paths.get("fooFile")));
		assertFalse(fileManager.removeTag("fooFile", "foo"));
	}

	@Test(expected = NoSuchFileException.class)
	public void testP2RemoveTagNoFileExist() throws Exception {
		fileManager.init(Collections.singletonList(Paths.get("fooFile")));
		fileManager.addTag("foo");
		assertFalse(fileManager.removeTag("blah", "foo"));
	}

	@Test
	public void testP3CatAll() throws Exception {
		fileManager.init(Command.listAllFiles());
		assertNotNull(fileManager.catAllFiles("untagged"));
	}

	@Test(expected = NoSuchTagException.class)
	public void testP3CatAllNoSuchTag() throws Exception {
		fileManager.init(Command.listAllFiles());
		assertNotNull(fileManager.catAllFiles("foo"));
	}

	@Test
	public void testP3EchoAll() throws Exception {
		fileManager.init(Command.listAllFiles());
		fileManager.echoToAllFiles("untagged", "test");
	}

	@Test(expected = NoSuchTagException.class)
	public void testP3EchoAllNoSuchTag() throws Exception {
		fileManager.init(Command.listAllFiles());
		fileManager.echoToAllFiles("foo", "test");
	}

}
