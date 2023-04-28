package jhi.gridscore.server.util;

import java.io.*;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.util.*;

public class FileUtils
{
	/**
	 * Checks, whether the child directory is a subdirectory of the base
	 * directory.
	 *
	 * @param base  the base directory.
	 * @param child the suspected child directory.
	 * @return true, if the child is a subdirectory of the base directory.
	 * @throws IOException if an IOError occured during the test.
	 */
	public static boolean isSubDirectory(File base, File child)
	{
		try
		{
			base = base.getCanonicalFile();
			child = child.getCanonicalFile();
		}
		catch (IOException e)
		{
			return false;
		}

		File parentFile = child;
		while (parentFile != null)
		{
			if (base.equals(parentFile))
			{
				return true;
			}
			parentFile = parentFile.getParentFile();
		}
		return false;
	}

	public static void zipUp(File folder)
	{
		File[] files = folder.listFiles();

		File zipFile = new File(folder, folder.getName() + ".zip");

		// Make sure it doesn't exist
		if (zipFile.exists())
			zipFile.delete();

		String prefix = zipFile.getAbsolutePath().replace("\\", "/");
		if (prefix.startsWith("/"))
			prefix = prefix.substring(1);
		URI uri = URI.create("jar:file:/" + prefix);
		Map<String, String> env = new HashMap<>();
		env.put("create", "true");
		env.put("encoding", "UTF-8");

		try (FileSystem fs = FileSystems.newFileSystem(uri, env, null))
		{
			if (files != null)
			{
				for (File f : files)
					Files.copy(f.toPath(), fs.getPath("/" + f.getName()), StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}
