package jhi.gridscore.server.resource;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jhi.gridscore.server.PropertyWatcher;
import jhi.gridscore.server.database.Database;
import jhi.gridscore.server.database.codegen.tables.records.TrialsRecord;
import jhi.gridscore.server.pojo.*;
import net.logicsquad.nanocaptcha.content.LatinContentProducer;
import net.logicsquad.nanocaptcha.image.ImageCaptcha;
import net.logicsquad.nanocaptcha.image.backgrounds.FlatColorBackgroundProducer;
import org.apache.commons.collections4.CollectionUtils;
import org.jooq.DSLContext;
import org.jooq.tools.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.*;
import java.security.SecureRandom;
import java.sql.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.*;
import java.util.logging.Logger;

import static jhi.gridscore.server.database.codegen.tables.Trials.TRIALS;

@Path("trial")
public class TrialResource
{
	private static final SecureRandom   RANDOM  = new SecureRandom();
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private String generateId()
	{
		byte[] buffer = new byte[20];
		RANDOM.nextBytes(buffer);
		return ENCODER.encodeToString(buffer);
	}

	private static final Map<String, String> captchaMap = Collections.synchronizedMap(new LinkedHashMap<>(1000)
	{
		@Override
		protected synchronized boolean removeEldestEntry(Map.Entry<String, String> entry)
		{
			return size() > 1000;
		}
	});

	@GET
	@Path("/{shareCode}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getTrialById(@PathParam("shareCode") String shareCode)
			throws SQLException
	{
		if (StringUtils.isBlank(shareCode))
			return Response.status(Response.Status.BAD_REQUEST).build();

		try (Connection conn = Database.getConnection())
		{
			DSLContext context = Database.getContext(conn);

			TrialsRecord trial = context.selectFrom(TRIALS)
										.where(TRIALS.OWNER_CODE.eq(shareCode)
																.or(TRIALS.EDITOR_CODE.eq(shareCode))
																.or(TRIALS.VIEWER_CODE.eq(shareCode)))
										.fetchAny();

			if (trial == null)
				return Response.status(Response.Status.NOT_FOUND).build();

			Trial result = trial.getTrial();
			setShareCodes(result, shareCode, trial);

			return Response.ok(result).build();
		}
	}

	public static void setShareCodes(Trial result, String baseShareCode, TrialsRecord trial)
	{
		ShareCodes codes = new ShareCodes();
		if (StringUtils.equals(baseShareCode, trial.getOwnerCode()))
		{
			codes.setOwnerCode(trial.getOwnerCode())
				 .setEditorCode(trial.getEditorCode())
				 .setViewerCode(trial.getViewerCode());
		}
		else if (StringUtils.equals(baseShareCode, trial.getEditorCode()))
		{
			codes.setEditorCode(trial.getEditorCode())
				 .setViewerCode(trial.getViewerCode());
		}
		else if (StringUtils.equals(baseShareCode, trial.getViewerCode()))
		{
			codes.setViewerCode(trial.getViewerCode());
		}

		result.setShareCodes(codes);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/checkupdate")
	public Response postCheckUpdate(List<String> ids)
			throws SQLException
	{
		if (CollectionUtils.isEmpty(ids))
			return Response.ok(new HashMap<>()).build();

		try (Connection conn = Database.getConnection())
		{
			DSLContext context = Database.getContext(conn);

			Map<String, TrialTimestamp> result = new HashMap<>();

			for (String id : ids)
			{
				TrialsRecord trial = context.selectFrom(TRIALS)
											.where(TRIALS.OWNER_CODE.eq(id)
																	.or(TRIALS.EDITOR_CODE.eq(id))
																	.or(TRIALS.VIEWER_CODE.eq(id)))
											.fetchAny();

				if (trial != null)
				{
					TrialTimestamp time = new TrialTimestamp()
							.setUpdatedOn(trial.getTrial().getUpdatedOn());
					try
					{
						ZonedDateTime updatedOn = ZonedDateTime.parse(trial.getTrial().getUpdatedOn(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX"));
						// Check how soon a trial will expire after inactivity
						Integer daysTillExpiry = Integer.parseInt(PropertyWatcher.get("trial.expiry.days"));
						// Add that number of days to the updatedOn date
						updatedOn = updatedOn.plusDays(daysTillExpiry);

						// Then check how long that date is from today!
						long timeTillExpiry = ZonedDateTime.now(ZoneOffset.UTC).until(updatedOn, ChronoUnit.DAYS);

						// Set the expiry warning if we're still before the expiry date but within a quarter of the overall expiry duration away
						time.setShowExpiryWarning(timeTillExpiry < (0.25 * daysTillExpiry));
						// Also set th expiry date
						time.setExpiresOn(updatedOn.format(new DateTimeFormatterBuilder().appendInstant(3).toFormatter()));
					}
					catch (Exception e)
					{
						Logger.getLogger("").severe(e.getMessage());
						// Do nothing here
					}

					result.put(id, time);
				}
				else
				{
					result.put(id, null);
				}
			}

			return Response.ok(result).build();
		}
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/share")
	public Response postShareTrial(Trial trial)
			throws SQLException
	{
		if (trial == null)
			return Response.status(Response.Status.BAD_REQUEST).build();

		// This trial has been shared before
		if (trial.getShareCodes() != null)
		{
			if (!StringUtils.isBlank(trial.getShareCodes().getOwnerCode()))
			{
				try (Connection conn = Database.getConnection())
				{
					DSLContext context = Database.getContext(conn);

					TrialsRecord match = context.selectFrom(TRIALS)
												.where(TRIALS.OWNER_CODE.eq(trial.getShareCodes().getOwnerCode()))
												.fetchAny();

					if (match != null)
					{
						// A match still exists, the client should not POST this trial again
						return Response.status(Response.Status.CONFLICT).build();
					}
					else
					{
						ShareCodes codes = trial.getShareCodes();
						// Remove any share codes
						trial.setShareCodes(null);

						ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
						trial.setUpdatedOn(now.format(new DateTimeFormatterBuilder().appendInstant(3).toFormatter()));

						// The trial doesn't exist anymore, it may have been deleted, store it again using same share codes.
						TrialsRecord record = context.newRecord(TRIALS);
						record.setTrial(trial);
						record.setOwnerCode(codes.getOwnerCode());
						record.setEditorCode(codes.getEditorCode());
						record.setViewerCode(codes.getViewerCode());
						record.setUpdatedOn(now.toLocalDateTime());
						record.store();

						// Add them again
						trial.setShareCodes(codes);

						return Response.ok(trial).build();
					}
				}
			}
			else
			{
				// Viewers cannot share the trial again
				return Response.status(Response.Status.FORBIDDEN).build();
			}
		}

		String ownerCode = generateId();
		String editorCode = generateId();
		String viewerCode = generateId();

		// Remove any share codes
		trial.setShareCodes(null);

		try (Connection conn = Database.getConnection())
		{
			DSLContext context = Database.getContext(conn);

			ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
			trial.setUpdatedOn(now.format(new DateTimeFormatterBuilder().appendInstant(3).toFormatter()));

			TrialsRecord record = context.newRecord(TRIALS);
			record.setTrial(trial);
			record.setOwnerCode(ownerCode);
			record.setEditorCode(editorCode);
			record.setViewerCode(viewerCode);
			record.setUpdatedOn(now.toLocalDateTime());
			record.store();
		}

		// Set them for the response
		trial.setShareCodes(new ShareCodes()
									.setOwnerCode(ownerCode)
									.setEditorCode(editorCode)
									.setViewerCode(viewerCode));

		return Response.ok(trial).build();
	}

	@POST
	@Path("/{shareCode}/renew/{uuid}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response postRenewCaptchaResponse(@PathParam("shareCode") String shareCode, @PathParam("uuid") String uuid, String captchaContent)
			throws SQLException
	{
		if (StringUtils.isBlank(shareCode))
			return Response.status(Response.Status.BAD_REQUEST).build();

		try (Connection conn = Database.getConnection())
		{
			DSLContext context = Database.getContext(conn);

			TrialsRecord wrapper = context.selectFrom(TRIALS)
										  .where(TRIALS.OWNER_CODE.eq(shareCode)
																  .or(TRIALS.EDITOR_CODE.eq(shareCode)))
										  .fetchAny();

			if (wrapper == null)
				return Response.status(Response.Status.NOT_FOUND).build();

			// Synchronize on the map to be sure
			synchronized (captchaMap)
			{
				String mapCaptcha = captchaMap.get(uuid);

				// Check if the captcha is correct
				if (StringUtils.isEmpty(mapCaptcha) || !Objects.equals(mapCaptcha, captchaContent))
					return Response.status(Response.Status.NOT_FOUND).build();

				synchronized (wrapper.getOwnerCode())
				{
					// Fetch it again once we're in the synchronised block
					wrapper = context.selectFrom(TRIALS)
									 .where(TRIALS.OWNER_CODE.eq(shareCode)
															 .or(TRIALS.EDITOR_CODE.eq(shareCode))
															 .or(TRIALS.VIEWER_CODE.eq(shareCode)))
									 .fetchAny();

					// Set updated on to UTC NOW
					ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
					wrapper.getTrial().setUpdatedOn(now.format(new DateTimeFormatterBuilder().appendInstant(3).toFormatter()));
					wrapper.setUpdatedOn(now.toLocalDateTime());
					wrapper.store();

					// Remove it from the map if all is successful
					captchaMap.remove(uuid);

					return Response.ok().build();
				}
			}
		}
	}

	@GET
	@Path("/{shareCode}/captcha/{uuid}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces({"image/png"})
	public Response getRenewCaptcha(@PathParam("shareCode") String shareCode, @PathParam("uuid") String uuid)
			throws SQLException
	{
		if (StringUtils.isBlank(shareCode))
			return Response.status(Response.Status.BAD_REQUEST).build();

		try
		{
			try (Connection conn = Database.getConnection())
			{
				DSLContext context = Database.getContext(conn);

				TrialsRecord trial = context.selectFrom(TRIALS)
											.where(TRIALS.OWNER_CODE.eq(shareCode)
																	.or(TRIALS.EDITOR_CODE.eq(shareCode)))
											.fetchAny();

				if (trial == null)
					return Response.status(Response.Status.NOT_FOUND).build();

				// Create a captcha with noise
				ImageCaptcha imageCaptcha = new ImageCaptcha.Builder(200, 50)
						.addNoise()
						.addContent(new LatinContentProducer(7))
						.addBackground(new FlatColorBackgroundProducer(Color.WHITE))
						.build();

				// Write to image
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ImageIO.write(imageCaptcha.getImage(), "png", baos);
				byte[] imageData = baos.toByteArray();

				// Remember captcha mapped to the uuid
				captchaMap.put(uuid, imageCaptcha.getContent());

				// Send image
				return Response.ok(new ByteArrayInputStream(imageData)).build();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
			Logger.getLogger("").severe(e.getLocalizedMessage());
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						   .build();
		}
	}
}
