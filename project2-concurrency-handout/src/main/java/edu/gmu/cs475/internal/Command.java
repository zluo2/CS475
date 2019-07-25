package edu.gmu.cs475.internal;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import edu.gmu.cs475.FileTagManager;
import edu.gmu.cs475.Tag;
import edu.gmu.cs475.TaggedFile;
import edu.gmu.cs475.AbstractFileTagManager;
import edu.gmu.cs475.struct.NoSuchTagException;
import edu.gmu.cs475.struct.ITag;
import edu.gmu.cs475.struct.ITaggedFile;
import edu.gmu.cs475.struct.TagExistsException;

@ShellComponent
public class Command {

    static final int SEQUENTIAL_THRESHOLD = 5000;
    
	AbstractFileTagManager service = new FileTagManager();

	public Command() {
		service = new FileTagManager();
		try {
			service.init(listAllFiles());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static final List<Path> listAllFiles() throws IOException {
		return Files.walk(AbstractFileTagManager.BASEDIR).filter(Files::isRegularFile).collect(Collectors.toList());
	}

	@ShellMethod("List the files")
	public CharSequence listFiles(String tag) {
		try {
			StringBuilder ret = new StringBuilder();
			for (ITaggedFile t : service.listFilesByTag(tag)) {
				ret.append(' ' + t.getName());
			}
			return ret.toString();
		} catch (NoSuchTagException ex) {
			return new AttributedString("Error: Tag " + tag + " does not exist", AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		}
	}

	@ShellMethod("List the tags")
	public CharSequence tags() {
		StringBuilder ret = new StringBuilder();
		for (ITag t : service.listTags()) {
			ret.append(' ' + t.getName());
		}
		return ret.toString();
	}

	@ShellMethod("Add a tag to a file")
	public CharSequence addTag(String tag) {
		try {
			service.addTag(tag);
		} catch (TagExistsException ex) {
			return new AttributedString("Error: Tag " + tag + " exists", AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		}
		return null;
	}

	@ShellMethod("Edit tag name")
	public CharSequence editTag(String oldTagName, String newTagName) {
		try {
			service.editTag(oldTagName, newTagName);
		} catch (NoSuchTagException ex) {
			return new AttributedString("Error: Tag " + oldTagName + " does not exist", AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		} catch (TagExistsException ex) {
			return new AttributedString("Error: Tag " + newTagName + " exists", AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		}
		return null;
	}

	@ShellMethod("Delete a tag")
	public CharSequence deleteTag(String tagName) {
		try {
			service.deleteTag(tagName);

		} catch (NoSuchTagException ex) {
			return new AttributedString("Error: Tag " + tagName + " does not exist", AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		} catch (DirectoryNotEmptyException e) {
			return new AttributedString("Error: Tag " + tagName + " is not empty", AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		}
		return null;
	}

	@ShellMethod("Add a tag to a file")
	public CharSequence tagFile(String file, String tag) {
		try {
			service.tagFile(file, tag);

		} catch (NoSuchTagException ex) {
			return new AttributedString("Error: Tag " + tag + " does not exist", AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		} catch (NoSuchFileException ex) {
			return new AttributedString("Error: File " + file + " does not exist", AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		}
		return null;
	}

	@ShellMethod("Remove a tag from a file")
	public CharSequence removeTag(String file, String tag) {
		try {
			if (!service.removeTag(file, tag))
				return new AttributedString("Error: Tag " + tag + " does not exist on file " + file, AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		} catch (NoSuchTagException ex) {
			return new AttributedString("Error: Tag " + tag + " does not exist", AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		} catch (NoSuchFileException ex) {
			return new AttributedString("Error: File " + file + " does not exist", AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		}
		return null;
	}

	@ShellMethod("List all tags on a file")
	public CharSequence getTags(String file) {
		try {
			StringBuilder ret = new StringBuilder();
			for (ITag t : service.getTags(file)) {
				ret.append(' ' + t.getName());
			}
			return ret.toString();
		} catch (NoSuchFileException ex) {
			return new AttributedString("Error: File " + file + " does not exist", AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		}
	}

	@ShellMethod("Cat a file")
	public CharSequence cat(String file) {
		try {
			return service.readFile(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	@ShellMethod("Echo text into a file")
	public CharSequence echo(String file, String text) {
		try {
			service.writeFile(file, text);
		} catch (IOException e) {
			return new AttributedString("Error: " + e.getMessage(), AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		}
		return null;
	}

	@ShellMethod("Cat all files matching a tag")
	public CharSequence catAll(String tag) {
		try {
			return service.catAllFiles(tag);
		} catch (NoSuchTagException ex) {
			return new AttributedString("Error: Tag " + tag + " does not exist", AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		} catch (IOException e) {
			return new AttributedString("Error: " + e.getMessage(), AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		}
	}

	@ShellMethod("Echo text into all files matching a tag")
	public CharSequence echoAll(String tag, String text) {
		try {
			service.echoToAllFiles(tag, text);
			return null;
		} catch (NoSuchTagException ex) {
			return new AttributedString("Error: Tag " + tag + " does not exist", AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		} catch (IOException e) {
			return new AttributedString("Error: " + e.getMessage(), AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		}
	}
}
