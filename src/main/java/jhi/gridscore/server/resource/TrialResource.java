package jhi.gridscore.server.resource;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jhi.gridscore.server.database.Database;
import jhi.gridscore.server.database.codegen.tables.pojos.Trials;
import jhi.gridscore.server.database.codegen.tables.records.TrialsRecord;
import jhi.gridscore.server.pojo.*;
import org.jooq.DSLContext;
import org.jooq.tools.StringUtils;

import java.security.SecureRandom;
import java.sql.*;
import java.util.Base64;

import static jhi.gridscore.server.database.codegen.tables.Trials.*;

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

			Trials trial = context.selectFrom(TRIALS)
								  .where(TRIALS.OWNER_CODE.eq(shareCode))
								  .or(TRIALS.EDITOR_CODE.eq(shareCode))
								  .or(TRIALS.VIEWER_CODE.eq(shareCode))
								  .fetchAnyInto(Trials.class);

			if (trial == null)
				return Response.status(Response.Status.NOT_FOUND).build();

			Trial result = trial.getTrial();
			setShareCodes(result, shareCode, trial);

			return Response.ok(result).build();
		}
	}

	private void setShareCodes(Trial result, String baseShareCode, Trials trial)
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
	@Path("/share")
	public Response postShareTrial(Trial trial)
		throws SQLException
	{
		if (trial == null)
			return Response.status(Response.Status.BAD_REQUEST).build();

		if (trial.getShareCodes() != null && (!StringUtils.isBlank(trial.getShareCodes().getOwnerCode()) || !StringUtils.isBlank(trial.getShareCodes().getEditorCode()) || !StringUtils.isBlank(trial.getShareCodes().getViewerCode())))
			return Response.status(Response.Status.CONFLICT).build();

		String ownerCode = generateId();
		String editorCode = generateId();
		String viewerCode = generateId();

		// Remove any share codes
		trial.setShareCodes(null);

		try (Connection conn = Database.getConnection())
		{
			DSLContext context = Database.getContext(conn);

			TrialsRecord record = context.newRecord(TRIALS);
			record.setTrial(trial);
			record.setOwnerCode(ownerCode);
			record.setEditorCode(editorCode);
			record.setViewerCode(viewerCode);
			record.store();
		}

		// Set them for the response
		trial.setShareCodes(new ShareCodes()
			.setOwnerCode(ownerCode)
			.setEditorCode(editorCode)
			.setViewerCode(viewerCode));

		return Response.ok(trial).build();
	}
}
