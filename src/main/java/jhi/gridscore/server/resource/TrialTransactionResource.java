package jhi.gridscore.server.resource;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jhi.gridscore.server.database.Database;
import jhi.gridscore.server.database.codegen.tables.records.TrialsRecord;
import jhi.gridscore.server.pojo.*;
import jhi.gridscore.server.pojo.transaction.*;
import org.apache.commons.collections4.CollectionUtils;
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
	public Response postTransactions(Transaction transaction)
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
											  .where(TRIALS.OWNER_CODE.eq(shareCode)
																	  .or(TRIALS.EDITOR_CODE.eq(shareCode))
																	  .or(TRIALS.VIEWER_CODE.eq(shareCode)))
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
									 .where(TRIALS.OWNER_CODE.eq(shareCode)
															 .or(TRIALS.EDITOR_CODE.eq(shareCode))
															 .or(TRIALS.VIEWER_CODE.eq(shareCode)))
									 .fetchAny();

					Trial trial = wrapper.getTrial();

					// If there's nothing to do, simply return
					if (transaction == null)
						return Response.ok(trial).build();

					/* ADD TRAITS */
					if (!CollectionUtils.isEmpty(transaction.getTrialTraitAddedTransactions()))
						trial.getTraits().addAll(transaction.getTrialTraitAddedTransactions());

					/* ADD TRIAL GERMPLASM */
					if (!CollectionUtils.isEmpty(transaction.getTrialGermplasmAddedTransactions()))
					{
						int counter = 0;
						for (String germplasm : transaction.getTrialGermplasmAddedTransactions())
						{
							// Create new cell
							Cell cell = new Cell()
									.setGermplasm(germplasm)
									.setRep(null);

							// Add to data map
							trial.getData().put(trial.getLayout().getRows() + counter + "|" + (trial.getLayout().getColumns() - 1), cell);

							counter++;
						}

						// Increment row count
						trial.getLayout().setRows(trial.getLayout().getRows() + counter);
					}

					for (Map.Entry<String, Cell> entry : trial.getData().entrySet())
					{
						String key = entry.getKey();
						Cell cell = entry.getValue();

						// Make sure the measurements object is set
						if (cell.getMeasurements() == null)
							cell.setMeasurements(new HashMap<>());

						// Make sure a measurements array exists for each trait (including any new traits that were added during this transaction)
						for (Trait t : trial.getTraits())
						{
							if (!cell.getMeasurements().containsKey(t.getId()))
								cell.getMeasurements().put(t.getId(), new ArrayList<>());
						}

						// Check if the marked status has changed
						if (transaction.getPlotMarkedTransactions() != null)
						{
							Boolean change = transaction.getPlotMarkedTransactions().get(key);

							if (change != null)
								cell.setIsMarked(change);
						}

						// Check if comments have been added
						if (transaction.getPlotCommentAddedTransactions() != null)
						{
							List<PlotCommentContent> plotComments = transaction.getPlotCommentAddedTransactions().get(key);

							if (!CollectionUtils.isEmpty(plotComments))
							{
								if (cell.getComments() == null)
									cell.setComments(new ArrayList<>());

								// Add all new comments
								cell.getComments().addAll(plotComments.stream().map(c -> new Comment().setContent(c.getContent()).setTimestamp(c.getTimestamp())).collect(Collectors.toList()));
							}
						}

						// Check if comments have been deleted
						if (transaction.getPlotCommentDeletedTransactions() != null)
						{
							List<PlotCommentContent> plotComments = transaction.getPlotCommentDeletedTransactions().get(key);

							if (!CollectionUtils.isEmpty(plotComments))
							{
								if (cell.getComments() != null)
								{
									// Remove all comments that match the timestamp AND content
									cell.getComments().removeIf(c -> plotComments.stream().anyMatch(oc -> Objects.equals(c.getContent(), oc.getContent()) && Objects.equals(c.getTimestamp(), oc.getTimestamp())));
								}
							}
						}

						// Check if any data has changed
						if (transaction.getPlotTraitDataChangeTransactions() != null)
						{
							List<TraitMeasurement> measures = transaction.getPlotTraitDataChangeTransactions().get(key);

							if (!CollectionUtils.isEmpty(measures))
							{
								Map<String, List<Measurement>> cellMeasurements = cell.getMeasurements();
								for (TraitMeasurement m : measures)
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

					/* ADD TRIAL COMMENTS */
					if (!CollectionUtils.isEmpty(transaction.getTrialCommentAddedTransactions()))
					{
						// Make sure the list exists
						if (trial.getComments() == null)
							trial.setComments(new ArrayList<>());

						List<TrialCommentContent> comments = transaction.getTrialCommentAddedTransactions();
						comments.sort(Comparator.comparing(TrialCommentContent::getTimestamp));

						// Add the new comment
						comments.forEach(c -> trial.getComments().add(new Comment().setContent(c.getContent())
																				   .setTimestamp(c.getTimestamp())));
					}

					/* REMOVE TRIAL COMMENTS */
					if (!CollectionUtils.isEmpty(transaction.getTrialCommentDeletedTransactions()))
					{
						if (trial.getComments() != null)
						{
							List<TrialCommentContent> comments = transaction.getTrialCommentDeletedTransactions();
							// Remove all comments that match the timestamp AND content
							trial.getComments().removeIf(c -> comments.stream().anyMatch(oc -> Objects.equals(c.getContent(), oc.getContent()) && Objects.equals(c.getTimestamp(), oc.getTimestamp())));
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
