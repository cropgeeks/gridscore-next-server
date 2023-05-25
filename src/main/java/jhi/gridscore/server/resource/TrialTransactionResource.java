package jhi.gridscore.server.resource;

import com.google.gson.Gson;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jhi.gridscore.server.database.Database;
import jhi.gridscore.server.database.codegen.tables.records.TrialsRecord;
import jhi.gridscore.server.pojo.*;
import jhi.gridscore.server.pojo.transaction.*;
import org.jooq.DSLContext;
import org.jooq.tools.StringUtils;

import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.stream.Collectors;

import static jhi.gridscore.server.database.codegen.tables.Trials.TRIALS;

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
											  .where(TRIALS.OWNER_CODE.eq(shareCode))
											  .or(TRIALS.EDITOR_CODE.eq(shareCode))
											  .or(TRIALS.VIEWER_CODE.eq(shareCode))
											  .fetchAny();

				if (wrapper == null)
				{
					return Response.status(Response.Status.NOT_FOUND)
								   .build();
				}
				if (Objects.equals(shareCode, wrapper.getViewerCode()))
				{
					return Response.status(Response.Status.FORBIDDEN)
								   .build();
				}


				synchronized (wrapper.getOwnerCode())
				{
					// Fetch it again once we're in the synchronised block
					wrapper = context.selectFrom(TRIALS)
									 .where(TRIALS.OWNER_CODE.eq(shareCode))
									 .or(TRIALS.EDITOR_CODE.eq(shareCode))
									 .or(TRIALS.VIEWER_CODE.eq(shareCode))
									 .fetchAny();

					Trial trial = wrapper.getTrial();

					// If there's nothing to do, simply return
					if (transactions == null || transactions.size() < 1)
						return Response.ok(trial).build();

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
							case TRAIT_DATA_CHANGED:
							{
								TraitDataContent content = gson.fromJson(t.getContent(), TraitDataContent.class);

								Cell cell = trial.getData().get(content.getRow() + "|" + content.getColumn());

								if (cell.getMeasurements() == null)
									cell.setMeasurements(new HashMap<>());

								Map<String, List<Measurement>> cellMeasurements = cell.getMeasurements();
								for (TraitMeasurement m : content.getMeasurements())
								{
									trial.getTraits().stream().filter(trait -> Objects.equals(trait.getId(), m.getTraitId())).findFirst()
										 .ifPresent(trait -> {
											 if (!cellMeasurements.containsKey(trait.getId()))
												 cellMeasurements.put(trait.getId(), new ArrayList<>());

											 List<Measurement> list = cellMeasurements.get(trait.getId());

											 if (m.getDelete())
											 {
												 if (trait.isAllowRepeats())
												 {
													 // Remove any value with the same timestamp
													 list.removeIf(om -> Objects.equals(m.getTimestamp(), om.getTimestamp()));
												 }
												 else
												 {
													 // Remove any value here
													 list.clear();
												 }
											 }
											 else
											 {
												 if (trait.isAllowRepeats() || list.size() < 1)
												 {
													 Optional<Measurement> match = list.stream().filter(om -> Objects.equals(m.getTimestamp(), om.getTimestamp())).findFirst();

													 if (match.isPresent())
													 {
														 match.get().setValues(m.getValues());
													 }
													 else
													 {
														 // Add new
														 list.add(new Measurement()
																 .setValues(m.getValues())
																 .setTimestamp(m.getTimestamp()));
													 }
												 }
												 else
												 {
													 // Update
													 list.get(0).setValues(m.getValues())
														 .setTimestamp(m.getTimestamp());
												 }
											 }
										 });
								}
							}
						}
					}

					// Set updated on to UTC NOW
					ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
					trial.setUpdatedOn(now.format(new DateTimeFormatterBuilder().appendInstant(3).toFormatter()));
					wrapper.setTrial(trial);
					wrapper.setUpdatedOn(now.toLocalDateTime());
					wrapper.store();

					// Limit the share codes to what the user is allowed to see
					TrialResource.setShareCodes(trial, shareCode, wrapper);

					return Response.ok(trial).build();
				}
			}
		}
	}
}
