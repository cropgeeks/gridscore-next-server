package jhi.gridscore.server.util;

import com.google.gson.Gson;
import jhi.gridscore.server.PropertyWatcher;
import jhi.gridscore.server.database.Database;
import jhi.gridscore.server.database.codegen.tables.records.TrialsRecord;
import jhi.gridscore.server.pojo.Corners;
import jhi.gridscore.server.resource.TrialExportResource;
import org.apache.commons.io.FileUtils;
import org.jooq.DSLContext;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;

import static jhi.gridscore.server.database.codegen.tables.Trials.TRIALS;

public class ExpiredTrialExportTask implements Runnable
{
	@Override
	public void run()
	{
		Logger.getLogger("").info("RUNNING ExpiredTrialExportTask");
		try
		{
			int daysTillExpiry = Integer.parseInt(PropertyWatcher.get("trial.expiry.days", "365"));

			int count = 0;
			try (Connection conn = Database.getConnection())
			{
				DSLContext context = Database.getContext(conn);

				List<TrialsRecord> trials = context.selectFrom(TRIALS).fetch();

				for (TrialsRecord trial : trials)
				{
					ZonedDateTime updatedOn = ZonedDateTime.parse(trial.getTrial().getUpdatedOn(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX"));
					// Check how soon a trial will expire after inactivity

					// Add that number of days to the updatedOn date
					updatedOn = updatedOn.plusDays(daysTillExpiry);

					// Then check how long that date is from today!
					ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
					long timeTillExpiry = now.until(updatedOn, ChronoUnit.SECONDS);

					if (timeTillExpiry <= 0)
					{
						// Trial has expired
						exportTrial(trial);

						// Delete it now we don't need it anymore
						trial.delete();

						count++;
					}
				}
			}
			catch (NullPointerException | NumberFormatException e)
			{
				Logger.getLogger("").severe(e.getMessage());
				e.printStackTrace();
			}

			Logger.getLogger("").info("ExpiredTrialExportTask: Archived " + count + " trials");
		}
		catch (SQLException | IOException | URISyntaxException e)
		{
			Logger.getLogger("").severe(e.getMessage());
			e.printStackTrace();
		}
	}

	private static void exportTrial(TrialsRecord trial)
			throws SQLException, IOException, URISyntaxException
	{
		// Use the database name here as it's going to be unique per instance and usually path-safe
		String path = PropertyWatcher.get("database.name");
		File folder = new File(System.getProperty("java.io.tmpdir"), path);
		folder.mkdirs();
		String id = trial.getTrial().getUpdatedOn().replace(":", "-") + "." + trial.getOwnerCode() + "." + trial.getEditorCode();
		// Filename contains all share codes and the date
		File zipFile = new File(folder, id + ".zip");

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
			try (BufferedWriter bw = Files.newBufferedWriter(fs.getPath("/sql-dump.json"), StandardCharsets.UTF_8))
			{
				Gson gson = new Gson();
				bw.write(gson.toJson(trial.getTrial()));
			}

			Corners corners = trial.getTrial().getLayout().getCorners();
			if (corners != null && corners.isValid())
			{
				String uuid = UUID.randomUUID().toString();
				File subfolder = new File(folder, uuid);
				subfolder.mkdirs();
				TrialExportResource.exportShapefile(trial.getTrial(), subfolder, "shapefile");

				File[] generated = subfolder.listFiles();

				if (generated != null)
				{
					for (File f : generated)
					{
						// Copy it to the zip file
						Files.copy(f.toPath(), fs.getPath("/" + f.getName()), StandardCopyOption.REPLACE_EXISTING);
					}
				}

				FileUtils.deleteDirectory(subfolder);
			}

			URL resource = PropertyWatcher.class.getClassLoader().getResource("trials-data.xlsx");
			if (resource != null)
			{
				File template = new File(resource.toURI());

				String uuid = UUID.randomUUID().toString();
				folder.mkdirs();
				File sourceCopy = new File(folder, id + ".xlsx");
				Files.copy(template.toPath(), sourceCopy.toPath(), StandardCopyOption.REPLACE_EXISTING);
				File target = new File(folder, uuid + ".xlsx");

				new DataToSpreadsheet(template, target, trial.getTrial(), false)
						.run();

				// Delete the copy of the template file
				sourceCopy.delete();

				// Copy it to the zip file
				Files.copy(target.toPath(), fs.getPath("/germinate-template.xlsx"), StandardCopyOption.REPLACE_EXISTING);

				target.delete();
			}

			File traitImageFolder = new File(PropertyWatcher.get("config.folder"), "trait-images");
			traitImageFolder.mkdirs();

			File[] potentials = traitImageFolder.listFiles(fn -> fn.getName().startsWith(trial.getOwnerCode() + "-"));

			if (potentials != null)
			{
				for (File image : potentials)
				{
					// Copy it to the zip file
					Files.copy(image.toPath(), fs.getPath("/" + image.getName()), StandardCopyOption.REPLACE_EXISTING);
					// Then delete from the file system
					image.delete();
				}
			}
		}
	}
}
