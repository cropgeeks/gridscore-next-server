package jhi.gridscore.server.resource;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jhi.gridscore.server.PropertyWatcher;
import jhi.gridscore.server.database.Database;
import jhi.gridscore.server.database.codegen.tables.records.TrialsRecord;
import jhi.gridscore.server.pojo.*;
import jhi.gridscore.server.util.Secured;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.media.multipart.*;
import org.jooq.DSLContext;
import org.jooq.tools.StringUtils;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.logging.Logger;

import static jhi.gridscore.server.database.codegen.tables.Trials.TRIALS;

@Secured
@Path("trait/{shareCode:.+}/{traitId:.+}/img")
public class TraitImageResource
{
	@PathParam("shareCode")
	String shareCode;
	@PathParam("traitId")
	String traitId;

	public static void deleteForTrial(String ownerCode)
	{
		File traitImageFolder = new File(PropertyWatcher.get("config.folder"), "trait-images");
		traitImageFolder.mkdirs();

		// Get all files that are associated with this trial owner code
		File[] files = traitImageFolder.listFiles(fn -> fn.getName().startsWith(ownerCode));

		if (files != null)
		{
			// Then delete them all
			for (File file : files)
				file.delete();
		}
	}

	public static void deleteForTrialTraits(String ownerCode, List<String> traitIds)
	{
		File traitImageFolder = new File(PropertyWatcher.get("config.folder"), "trait-images");
		traitImageFolder.mkdirs();

		for (String traitId : traitIds)
		{
			// Get all files that are associated with this trial owner code and trait id
			File[] files = traitImageFolder.listFiles(fn -> fn.getName().startsWith(ownerCode + "-" + traitId + "."));

			if (files != null)
			{
				// Then delete them all
				for (File file : files)
					file.delete();
			}
		}
	}

	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_JSON)
	public Response postTraitImage(@FormDataParam("imageFile") InputStream fileIs, @FormDataParam("imageFile") FormDataContentDisposition fileDetails)
			throws SQLException, IOException
	{
		Logger.getLogger("").info("TRAIT IMAGE SHARE: " + shareCode + " + " + traitId + " -> " + fileDetails);

		if (StringUtils.isEmpty(shareCode) || fileIs == null || fileDetails == null)
		{
			return Response.status(Response.Status.BAD_REQUEST)
						   .build();
		}
		else
		{
			try (Connection conn = Database.getConnection())
			{
				DSLContext context = Database.getContext(conn);
				TrialsRecord wrapper = context.selectFrom(TRIALS)
											  .where(TRIALS.OWNER_CODE.eq(shareCode)
																	  .or(TRIALS.EDITOR_CODE.eq(shareCode))
																	  .or(TRIALS.VIEWER_CODE.eq(shareCode)))
											  .fetchAny();

				if (wrapper == null)
					return Response.status(Response.Status.NOT_FOUND)
								   .build();

				if (!Objects.equals(wrapper.getOwnerCode(), shareCode))
					return Response.status(Response.Status.FORBIDDEN)
								   .build();

				Trial trial = wrapper.getTrial();
				Optional<Trait> traitMatch = trial.getTraits().stream().filter(t -> Objects.equals(t.getId(), traitId)).findAny();

				if (traitMatch.isEmpty())
					return Response.status(Response.Status.NOT_FOUND)
								   .build();

				File traitImageFolder = new File(PropertyWatcher.get("config.folder"), "trait-images");
				traitImageFolder.mkdirs();

				String filename = fileDetails.getFileName();

				if (!filename.contains("."))
					return Response.status(Response.Status.BAD_REQUEST).build();

				String extension = filename.substring(filename.lastIndexOf(".") + 1);

				// Search for old matches (might have same or different extension)
				File[] potentials = traitImageFolder.listFiles(fn -> fn.getName().startsWith(wrapper.getOwnerCode() + "-" + traitId + "."));

				if (potentials != null)
				{
					for (File potential : potentials)
						potential.delete();
				}

				File imageFile = new File(traitImageFolder, wrapper.getOwnerCode() + "-" + traitId + "." + extension);

				Files.copy(fileIs, imageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

				// Mark this trait as having an image
				traitMatch.get().setHasImage(true);

				// Set updated on to UTC NOW
				ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
				String formattedNow = now.format(new DateTimeFormatterBuilder().appendInstant(3).toFormatter());
				trial.setUpdatedOn(formattedNow);
				wrapper.setTrial(trial);
				wrapper.setUpdatedOn(now.toLocalDateTime());
				wrapper.store();

				return Response.ok(true).build();
			}
		}
	}

	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces({"image/png", "image/jpeg", "image/*"})
	public Response getTraitImage()
			throws SQLException, IOException
	{
		if (StringUtils.isEmpty(shareCode))
		{
			return Response.status(Response.Status.BAD_REQUEST)
						   .build();
		}
		else
		{
			try (Connection conn = Database.getConnection())
			{
				DSLContext context = Database.getContext(conn);
				TrialsRecord wrapper = context.selectFrom(TRIALS)
											  .where(TRIALS.OWNER_CODE.eq(shareCode)
																	  .or(TRIALS.EDITOR_CODE.eq(shareCode))
																	  .or(TRIALS.VIEWER_CODE.eq(shareCode)))
											  .fetchAny();

				if (wrapper == null)
					return Response.status(Response.Status.NOT_FOUND)
								   .build();

				File traitImageFolder = new File(PropertyWatcher.get("config.folder"), "trait-images");
				traitImageFolder.mkdirs();

				File[] potentials = traitImageFolder.listFiles(fn -> fn.getName().startsWith(wrapper.getOwnerCode() + "-" + traitId + "."));

				if (potentials == null || potentials.length < 1)
					return Response.status(Response.Status.NOT_FOUND).build();

				File imageFile = potentials[0];

				if (imageFile.exists())
				{
					byte[] bytes = IOUtils.toByteArray(imageFile.toURI());

					return Response.ok(new ByteArrayInputStream(bytes))
								   .header("Content-Type", "image/png")
								   .build();
				}
				else
				{
					return Response.status(Response.Status.NOT_FOUND).build();
				}
			}
		}
	}
}
