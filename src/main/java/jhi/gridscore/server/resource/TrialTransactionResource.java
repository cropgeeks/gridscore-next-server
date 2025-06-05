package jhi.gridscore.server.resource;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jhi.gridscore.server.database.Database;
import jhi.gridscore.server.database.codegen.tables.records.TrialsRecord;
import jhi.gridscore.server.pojo.*;
import jhi.gridscore.server.pojo.transaction.*;
import jhi.gridscore.server.util.Secured;
import org.apache.commons.collections4.CollectionUtils;
import org.jooq.DSLContext;
import org.jooq.tools.StringUtils;

import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.stream.Collectors;

import static jhi.gridscore.server.database.codegen.tables.Trials.TRIALS;

@Secured
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

				// By default, let's assume they're the owner
				TrialPermissionType type = TrialPermissionType.OWNER;
				// Viewers cannot edit anything
				if (Objects.equals(shareCode, wrapper.getViewerCode()))
				{
					// Check owner and editor as well, though. We sometimes manually set all three codes to the same value which would prevent editing.
					if (!Objects.equals(shareCode, wrapper.getEditorCode()) && !Objects.equals(shareCode, wrapper.getOwnerCode()))
					{
						return Response.status(Response.Status.FORBIDDEN)
									   .build();
					}
				}
				else if (Objects.equals(shareCode, wrapper.getEditorCode()))
				{
					// They're an editor
					type = TrialPermissionType.EDITOR;
				}
				// If there's nothing to do, simply return
				if (transaction == null)
				{
					// Limit the share codes to what the user is allowed to see
					TrialResource.setShareCodes(wrapper.getTrial(), shareCode, wrapper);
					return Response.ok(wrapper.getTrial()).build();
				}
				// Only owners can update trial details and edit traits!
				if ((transaction.getTrialEditTransaction() != null || !CollectionUtils.isEmpty(transaction.getTraitChangeTransactions()) || !CollectionUtils.isEmpty(transaction.getTrialTraitDeletedTransactions())) && !Objects.equals(shareCode, wrapper.getOwnerCode()))
				{
					return Response.status(Response.Status.FORBIDDEN).build();
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

					/* Check for trial modifications */
					if (transaction.getTrialEditTransaction() != null)
					{
						TrialContent change = transaction.getTrialEditTransaction();
						trial.setName(change.getName());
						trial.setDescription(change.getDescription());
						trial.setSocialShareConfig(change.getSocialShareConfig());
						trial.getLayout().setCorners(change.getCorners());
						trial.getLayout().setMarkers(change.getMarkers());
					}

					/* ADD TRAITS */
					if (!CollectionUtils.isEmpty(transaction.getTrialTraitAddedTransactions()))
						trial.getTraits().addAll(transaction.getTrialTraitAddedTransactions());

					/* DELETE TRAITS */
					if (!CollectionUtils.isEmpty(transaction.getTrialTraitDeletedTransactions()))
					{
						// Remove them all
						List<String> ids = transaction.getTrialTraitDeletedTransactions().stream().map(Trait::getId).collect(Collectors.toList());
						trial.getTraits().removeIf(t -> ids.contains(t.getId()));

						for (Trait trait : transaction.getTrialTraitDeletedTransactions())
						{
							Map<String, Cell> data = trial.getData();

							for (Cell cell : data.values())
							{
								if (cell.getMeasurements() != null)
									cell.getMeasurements().remove(trait.getId());
							}
						}

						// Remove any stored reference images
						TraitImageResource.deleteForTrialTraits(wrapper.getOwnerCode(), ids);
					}

					/* ADD PEOPLE */
					if (!CollectionUtils.isEmpty(transaction.getTrialPersonAddedTransactions()))
					{
						if (trial.getPeople() == null)
							trial.setPeople(transaction.getTrialPersonAddedTransactions());
						else
							trial.getPeople().addAll(transaction.getTrialPersonAddedTransactions());
					}

					/* ADD TRIAL GERMPLASM */
					if (!CollectionUtils.isEmpty(transaction.getTrialGermplasmAddedTransactions()))
					{
						int counter = 0;
						for (String germplasm : transaction.getTrialGermplasmAddedTransactions())
						{
							// Create new cell
							Cell cell = new Cell()
									.setGermplasm(germplasm)
									.setCategories(new ArrayList<>())
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

						if (transaction.getBrapiIdChangeTransaction() != null && transaction.getBrapiIdChangeTransaction().getGermplasmBrapiIds() != null)
						{
							String id = transaction.getBrapiIdChangeTransaction().getGermplasmBrapiIds().get(key);

							if (!StringUtils.isBlank(id))
								cell.setBrapiId(id);
						}

						// Make sure the measurements object is set
						if (cell.getMeasurements() == null)
							cell.setMeasurements(new HashMap<>());

						// Check if the trial corners have been moved, if so adjust the plot corners accordingly
						if (transaction.getTrialEditTransaction() != null && transaction.getTrialEditTransaction().getPlotCorners() != null)
						{
							Map<String, Corners> pc = transaction.getTrialEditTransaction().getPlotCorners();

							cell.getGeography().setCorners(pc.getOrDefault(key, null));
						}

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

						// Check if the plot geography (center) has changed
						if (transaction.getPlotGeographyChangeTransactions() != null)
						{
							PlotGeographyContent geography = transaction.getPlotGeographyChangeTransactions().get(key);

							if (geography != null)
							{
								if (cell.getGeography() != null && cell.getGeography().getCenter() != null)
								{
									// Geography and center exist, so take average
									LatLng cellCenter = cell.getGeography().getCenter();

									if (cellCenter.getLat() != null)
										cellCenter.setLat((cellCenter.getLat() + geography.getCenter().getLat()) / 2);
									else
										cellCenter.setLat(geography.getCenter().getLat());
									if (cellCenter.getLng() != null)
										cellCenter.setLng((cellCenter.getLng() + geography.getCenter().getLng()) / 2);
									else
										cellCenter.setLng(geography.getCenter().getLng());
								}
								else
								{
									// There's no geography or no center. Make sure the geography exists.
									if (cell.getGeography() == null)
										cell.setGeography(new Geography());

									// Then set the center.
									cell.getGeography().setCenter(geography.getCenter());
								}
							}
						}

						// Check if plot details have changed
						if (transaction.getPlotDetailsChangeTransaction() != null)
						{
							PlotDetailContent plotChanges = transaction.getPlotDetailsChangeTransaction().get(key);

							if (plotChanges != null)
							{
								cell.setBarcode(plotChanges.getBarcode())
									.setFriendlyName(plotChanges.getFriendlyName())
									.setPedigree(plotChanges.getPedigree())
									.setTreatment(plotChanges.getTreatment());
							}
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
												 if (trait.isAllowRepeats() || list.isEmpty())
												 {
													 Optional<Measurement> match = list.stream().filter(om -> Objects.equals(m.getTimestamp(), om.getTimestamp())).findFirst();

													 if (match.isPresent())
													 {
														 match.get()
															  .setPersonId(m.getPersonId())
															  .setValues(m.getValues());
													 }
													 else
													 {
														 // Add new
														 list.add(new Measurement()
																 .setPersonId(m.getPersonId())
																 .setValues(m.getValues())
																 .setTimestamp(m.getTimestamp()));
													 }
												 }
												 else
												 {
													 // Update
													 list.get(0)
														 .setPersonId(m.getPersonId())
														 .setValues(m.getValues())
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

					/* ADD TRIAL EVENT */
					if (!CollectionUtils.isEmpty(transaction.getTrialEventAddedTransactions()))
					{
						// Make sure the list exists
						if (trial.getEvents() == null)
							trial.setEvents(new ArrayList<>());

						List<TrialEventContent> events = transaction.getTrialEventAddedTransactions();
						events.sort(Comparator.comparing(TrialEventContent::getTimestamp));

						// Add the new event
						events.forEach(c -> trial.getEvents().add(new Event().setContent(c.getContent())
																			 .setTimestamp(c.getTimestamp())
																			 .setType(c.getType())
																			 .setImpact(c.getImpact())));
					}

					/* REMOVE TRIAL EVENT */
					if (!CollectionUtils.isEmpty(transaction.getTrialEventDeletedTransactions()))
					{
						if (trial.getEvents() != null)
						{
							List<TrialEventContent> events = transaction.getTrialEventDeletedTransactions();
							// Remove all events that match the timestamp AND content
							trial.getEvents().removeIf(c -> events.stream().anyMatch(oc -> Objects.equals(c.getContent(), oc.getContent())
									&& Objects.equals(c.getTimestamp(), oc.getTimestamp())
									&& Objects.equals(c.getType(), oc.getType())
									&& Objects.equals(c.getImpact(), oc.getImpact())));
						}
					}

					/* CHANGE TRAIT BRAPI IDS */
					if (transaction.getBrapiIdChangeTransaction() != null && transaction.getBrapiIdChangeTransaction().getTraitBrapiIds() != null)
					{
						for (Trait trait : trial.getTraits())
						{
							String id = transaction.getBrapiIdChangeTransaction().getTraitBrapiIds().get(trait.getId());

							if (!StringUtils.isBlank(id))
								trait.setBrapiId(id);
						}
					}

					/* CHANGE TRAIT DETAILS */
					if (!CollectionUtils.isEmpty(transaction.getTraitChangeTransactions()))
					{
						// Sort them by date
						transaction.getTraitChangeTransactions().sort(Comparator.comparing(TraitEditContent::getTimestamp));

						List<String> traitIdsForImageDeletion = new ArrayList<>();
						for (TraitEditContent newTraitData : transaction.getTraitChangeTransactions())
						{
							trial.getTraits().stream().filter(t -> Objects.equals(t.getId(), newTraitData.getId())).findAny()
								 .ifPresent(oldTraitData -> {
									 // It used to have an image, but now it doesn't
									 if (oldTraitData.isHasImage() && newTraitData.getHasImage() != null && !newTraitData.getHasImage())
										 traitIdsForImageDeletion.add(oldTraitData.getId());

									 oldTraitData.setName(newTraitData.getName());
									 oldTraitData.setDescription(newTraitData.getDescription());
									 oldTraitData.setHasImage(newTraitData.getHasImage() != null ? newTraitData.getHasImage() : false);
									 oldTraitData.setImageUrl(newTraitData.getImageUrl());
									 if (StringUtils.isBlank(newTraitData.getGroup()))
										 oldTraitData.setGroup(null);
									 else
										 oldTraitData.setGroup(new Group().setName(newTraitData.getGroup()));
								 });
						}

						if (!CollectionUtils.isEmpty(traitIdsForImageDeletion))
							TraitImageResource.deleteForTrialTraits(trial.getShareCodes().getOwnerCode(), traitIdsForImageDeletion);
					}

					if (transaction.getBrapiConfigChangeTransaction() != null && !StringUtils.isBlank(transaction.getBrapiConfigChangeTransaction().getUrl()))
						trial.setBrapiConfig(new BrapiConfig().setUrl(transaction.getBrapiConfigChangeTransaction().getUrl()));

					// Set updated on to UTC NOW
					ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
					String formattedNow = now.format(new DateTimeFormatterBuilder().appendInstant(3).toFormatter());
					trial.setUpdatedOn(formattedNow);
					wrapper.setTrial(trial);

					UpdateStats stats = wrapper.getUpdateStats();

					if (stats == null)
						stats = new UpdateStats();

					if (type == TrialPermissionType.OWNER)
					{
						stats.getOwnerUpdates()
							 .setUpdateCount(stats.getOwnerUpdates().getUpdateCount() + 1)
							 .setLastUpdateOn(formattedNow);
					}
					else if (type == TrialPermissionType.EDITOR)
					{
						stats.getEditorUpdates()
							 .setUpdateCount(stats.getEditorUpdates().getUpdateCount() + 1)
							 .setLastUpdateOn(formattedNow);
					}

					wrapper.setUpdateStats(stats);
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
