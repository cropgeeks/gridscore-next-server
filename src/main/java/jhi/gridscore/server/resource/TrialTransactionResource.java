package jhi.gridscore.server.resource;

import com.google.gson.Gson;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jhi.gridscore.server.database.Database;
import jhi.gridscore.server.database.codegen.tables.pojos.Trials;
import jhi.gridscore.server.pojo.*;
import jhi.gridscore.server.pojo.transaction.*;
import org.jooq.DSLContext;
import org.jooq.tools.StringUtils;

import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.stream.Collectors;

import static jhi.gridscore.server.database.codegen.tables.Trials.*;

@Path("trial/{shareCode}/transaction")
public class TrialTransactionResource
{
	@PathParam("shareCode")
	String shareCode;

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response postTransactions(List<Transaction> transactions)
		throws SQLException
	{
		if (StringUtils.isEmpty(shareCode) || transactions == null)
		{
			return Response.status(Response.Status.BAD_REQUEST)
						   .build();
		}
		else
		{
			try (Connection conn = Database.getConnection())
			{
				DSLContext context = Database.getContext(conn);
				Trials wrapper = context.selectFrom(TRIALS)
										.where(TRIALS.OWNER_CODE.eq(shareCode))
										.or(TRIALS.EDITOR_CODE.eq(shareCode))
										.fetchAnyInto(Trials.class);

				if (wrapper == null)
				{
					return Response.status(Response.Status.NOT_FOUND)
								   .build();
				}

				Trial trial = wrapper.getTrial();

				// Sort the transactions in order of their timestamp to make sure everything happens in the correct order
				transactions.sort(Comparator.comparing(Transaction::getTimestamp));

				Gson gson = new Gson();
				for (Transaction t : transactions)
				{
					switch (t.getOperation())
					{
						case TRIAL_TRAITS_ADDED:
						{
							TraitContent content = gson.fromJson(t.getContent(), TraitContent.class);

							// Add all the new traits
							trial.getTraits().addAll(content);

							// Iterate all plots
							for (Cell cell : trial.getData().values())
							{
								// Make sure the measurements array exists
								if (cell.getMeasurements() == null)
									cell.setMeasurements(new HashMap<>());

								// Add keys with empty lists for each trait
								for (Trait trait : content)
									cell.getMeasurements().put(trait.getId(), new ArrayList<>());
							}

							break;
						}
						case TRIAL_COMMENT_ADDED:
						{
							TrialCommentContent content = gson.fromJson(t.getContent(), TrialCommentContent.class);

							// Make sure the list exists
							if (trial.getComments() == null)
								trial.setComments(new ArrayList<>());

							// Add the new comment
							trial.getComments().add(new Comment().setContent(content.getContent())
																 .setTimestamp(content.getTimestamp()));

							break;
						}
						case TRIAL_COMMENT_DELETED:
						{
							TrialCommentContent content = gson.fromJson(t.getContent(), TrialCommentContent.class);

							if (trial.getComments() != null)
							{
								// Remove all comments that match the timestamp AND content
								trial.setComments(trial.getComments().stream()
													   .filter(c -> !Objects.equals(c.getContent(), content.getContent()) || !Objects.equals(c.getTimestamp(), content.getTimestamp()))
													   .collect(Collectors.toList()));
							}

							break;
						}
						case PLOT_COMMENT_ADDED:
						{
							PlotCommentContent content = gson.fromJson(t.getContent(), PlotCommentContent.class);

							Cell cell = trial.getData().get(content.getRow() + "|" + content.getColumn());

							if (cell != null)
							{
								// Make sure the list exists
								if (cell.getComments() == null)
									cell.setComments(new ArrayList<>());

								// Add the new comment
								cell.getComments().add(new Comment().setContent(content.getContent())
																	.setTimestamp(content.getTimestamp()));
							}

							break;
						}
						case PLOT_COMMENT_DELETED:
						{
							PlotCommentContent content = gson.fromJson(t.getContent(), PlotCommentContent.class);

							Cell cell = trial.getData().get(content.getRow() + "|" + content.getColumn());

							if (cell != null)
							{
								// Remove all comments that match the timestamp AND content
								cell.setComments(cell.getComments().stream()
													 .filter(c -> !Objects.equals(c.getContent(), content.getContent()) || !Objects.equals(c.getTimestamp(), content.getTimestamp()))
													 .collect(Collectors.toList()));
							}

							break;
						}
						case PLOT_MARKED_CHANGED:
						{
							PlotMarkedContent content = gson.fromJson(t.getContent(), PlotMarkedContent.class);

							Cell cell = trial.getData().get(content.getRow() + "|" + content.getColumn());

							if (cell != null)
								cell.setIsMarked(content.getIsMarked());

							break;
						}
					}
				}

				// Set updated on to UTC NOW
				trial.setUpdatedOn(ZonedDateTime.now(ZoneOffset.UTC).format(new DateTimeFormatterBuilder().appendInstant(3).toFormatter()));

				return Response.ok(trial).build();
			}
		}
	}
}
