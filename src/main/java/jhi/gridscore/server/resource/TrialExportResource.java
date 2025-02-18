package jhi.gridscore.server.resource;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jhi.gridscore.server.PropertyWatcher;
import jhi.gridscore.server.database.Database;
import jhi.gridscore.server.database.codegen.tables.pojos.Trials;
import jhi.gridscore.server.pojo.*;
import jhi.gridscore.server.util.*;
import org.geotools.data.Transaction;
import org.geotools.data.*;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.*;
import org.geotools.data.simple.*;
import org.geotools.feature.simple.*;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.jooq.DSLContext;
import org.jooq.tools.StringUtils;
import org.locationtech.jts.geom.*;
import org.opengis.feature.simple.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.logging.Logger;

import static jhi.gridscore.server.database.codegen.tables.Trials.TRIALS;

@Secured
@Path("trial/{shareCode}/export")
public class TrialExportResource
{
	@PathParam("shareCode")
	String shareCode;

	@GET
	@Path("/archive/exists")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getTrialByIdArchiveExists()
	{
		if (StringUtils.isBlank(shareCode))
			return Response.status(Response.Status.BAD_REQUEST).build();

		// Use the database name here as it's going to be unique per instance and usually path-safe
		String path = PropertyWatcher.get("database.name");
		File folder = new File(System.getProperty("java.io.tmpdir"), path);

		File[] matches = folder.listFiles(filename -> {
			String[] parts = filename.getName().split("\\.");
			// Has to be 4 parts (datetime.ownerCode.editorCode.zip), last one has to be zip, and either 2nd or 3rd have to be the code
			return parts.length == 5 && parts[4].equals("zip") && (parts[2].equals(shareCode) || parts[3].equals(shareCode));
		});

		if (matches != null && matches.length > 0)
		{
			File m = matches[0];
			String[] parts = m.getName().split("\\.");

			// Get the created date from the file modified date
			ZonedDateTime exported = ZonedDateTime.ofInstant(Instant.ofEpochMilli(m.lastModified()), ZoneOffset.UTC);
			// Get the updated date from the file name
			ZonedDateTime updated = ZonedDateTime.parse(parts[0] + "." + parts[1], DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss.SSSXX"));
			DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

			ArchiveInformation info = new ArchiveInformation()
					.setFileSize(m.length())
					.setTrialExportedOn(exported.format(formatter))
					.setTrialUpdatedOn(updated.format(formatter));

			return Response.ok(info).build();
		}
		else
		{
			return Response.status(Response.Status.NOT_FOUND).build();
		}
	}

	@GET
	@Path("/archive")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getTrialByIdArchived()
	{
		if (StringUtils.isBlank(shareCode))
			return Response.status(Response.Status.BAD_REQUEST).build();

		// Use the database name here as it's going to be unique per instance and usually path-safe
		String path = PropertyWatcher.get("database.name");
		File folder = new File(System.getProperty("java.io.tmpdir"), path);

		File[] matches = folder.listFiles(filename -> {
			String[] parts = filename.getName().split("\\.");
			// Has to be 4 parts (datetime.ownerCode.editorCode.zip), last one has to be zip, and either 2nd or 3rd have to be the code
			return parts.length == 5 && parts[4].equals("zip") && (parts[2].equals(shareCode) || parts[3].equals(shareCode));
		});

		if (matches != null && matches.length > 0)
		{
			return Response.ok((StreamingOutput) output -> {
							   Files.copy(matches[0].toPath(), output);
						   })
						   .type("application/zip")
						   .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=\"" + matches[0].getName() + "\"")
						   .header(HttpHeaders.CONTENT_LENGTH, matches[0].length())
						   .build();
		}
		else
		{
			return Response.status(Response.Status.NOT_FOUND).build();
		}
	}

	@GET
	@Path("/shapefile")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response postConfigExportShapefile()
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
				Trials trials = context.selectFrom(TRIALS)
									   .where(TRIALS.OWNER_CODE.eq(shareCode))
									   .or(TRIALS.EDITOR_CODE.eq(shareCode))
									   .or(TRIALS.VIEWER_CODE.eq(shareCode))
									   .fetchAnyInto(Trials.class);

				if (trials == null)
				{
					return Response.status(Response.Status.NOT_FOUND)
								   .build();
				}
				else
				{
					Trial trial = trials.getTrial();

					/*
					 * Get an output file name and create the new shapefile
					 */
					String uuid = UUID.randomUUID().toString();
					File folder = new File(new File(System.getProperty("java.io.tmpdir"), "gridscore-next"), uuid);

					exportShapefile(trial, folder, uuid);

					FileUtils.zipUp(folder);

					return Response.ok(uuid).build();
				}
			}
			catch (MalformedURLException e)
			{
				// Template file wasn't found
				e.printStackTrace();
				Logger.getLogger("").severe(e.getMessage());
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
							   .build();
			}
		}
	}

	public static void exportShapefile(Trial trial, File folder, String filename)
			throws IOException
	{
		final SimpleFeatureType TYPE = createFeatureType();

		List<SimpleFeature> features = new ArrayList<>();

		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory(null);
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);

		for (Map.Entry<String, Cell> cellEntry : trial.getData().entrySet())
		{
			Cell cell = cellEntry.getValue();
			String[] rowColumn = cellEntry.getKey().split("\\|");
			int row = Integer.parseInt(rowColumn[0]);
			int col = Integer.parseInt(rowColumn[1]);
			Corners corners = cell.getGeography().getCorners();
			if (corners != null)
			{
				Coordinate[] coordinates = new Coordinate[]{
						new Coordinate(corners.getTopLeft().getLng(), corners.getTopLeft().getLat()),
						new Coordinate(corners.getBottomLeft().getLng(), corners.getBottomLeft().getLat()),
						new Coordinate(corners.getBottomRight().getLng(), corners.getBottomRight().getLat()),
						new Coordinate(corners.getTopRight().getLng(), corners.getTopRight().getLat()),
						new Coordinate(corners.getTopLeft().getLng(), corners.getTopLeft().getLat())
				};

				Polygon polygon = geometryFactory.createPolygon(coordinates);
				featureBuilder.add(polygon);
				featureBuilder.add(cell.getGermplasm());
				featureBuilder.add(row + 1);
				featureBuilder.add(col + 1);
				featureBuilder.add(cell.getRep());
				SimpleFeature feature = featureBuilder.buildFeature(null);
				features.add(feature);
			}
		}

		folder.mkdirs();
		File target = new File(folder, filename + ".shp");

		ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

		Map<String, Serializable> params = new HashMap<>();
		params.put("url", target.toURI().toURL());
		params.put("create spatial index", Boolean.TRUE);

		ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
		newDataStore.createSchema(TYPE);

		/*
		 * Write the features to the shapefile
		 */
		Transaction transaction = new DefaultTransaction("create");

		String typeName = newDataStore.getTypeNames()[0];
		SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);

		if (featureSource instanceof SimpleFeatureStore)
		{
			SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;

			/*
			 * SimpleFeatureStore has a method to add features from a
			 * SimpleFeatureCollection object, so we use the ListFeatureCollection
			 * class to wrap our list of features.
			 */
			SimpleFeatureCollection collection = new ListFeatureCollection(TYPE, features);
			featureStore.setTransaction(transaction);
			try
			{
				featureStore.addFeatures(collection);
				transaction.commit();

			}
			catch (Exception problem)
			{
				problem.printStackTrace();
				transaction.rollback();

			}
			finally
			{
				transaction.close();
			}
		}
	}

	@GET
	@Path("/shapefile/{uuid}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces("application/zip")
	public Response getConfigShapefileDownload(@PathParam("uuid") String uuid)
			throws IOException, SQLException
	{
		try (Connection conn = Database.getConnection())
		{
			DSLContext context = Database.getContext(conn);
			Trials trials = context.selectFrom(TRIALS)
								   .where(TRIALS.OWNER_CODE.eq(shareCode)
														   .or(TRIALS.EDITOR_CODE.eq(shareCode))
														   .or(TRIALS.VIEWER_CODE.eq(shareCode)))
								   .fetchAnyInto(Trials.class);

			File folder = new File(new File(System.getProperty("java.io.tmpdir"), "gridscore-next"), uuid);
			File result = new File(folder, uuid + ".zip");

			if (!FileUtils.isSubDirectory(folder, result))
				return Response.status(Response.Status.BAD_REQUEST).build();
			if (!result.exists() || !result.isFile())
				return Response.status(Response.Status.NOT_FOUND).build();

			String friendlyFilename = trials.getTrial().getName().replaceAll("\\W+", "-") + "-" + uuid;

			java.nio.file.Path zipFilePath = result.toPath();
			return Response.ok((StreamingOutput) output -> {
							   Files.copy(zipFilePath, output);
							   Files.deleteIfExists(zipFilePath);
							   org.apache.commons.io.FileUtils.deleteDirectory(folder);
						   })
						   .type("application/zip")
						   .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=\"" + friendlyFilename + ".zip\"")
						   .header(HttpHeaders.CONTENT_LENGTH, result.length())
						   .build();
		}
	}

	private static SimpleFeatureType createFeatureType()
	{

		SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
		builder.setName("Location");
		builder.setCRS(DefaultGeographicCRS.WGS84); // <- Coordinate reference system

		// add attributes in order
		builder.add("the_geom", Polygon.class);
		builder.add("germplasm", String.class); // <- 15 chars width for name field
		builder.add("row", Integer.class);
		builder.add("column", Integer.class);
		builder.add("rep", String.class);

		// build the type
		return builder.buildFeatureType();
	}

	@GET
	@Path("/g8")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getExportGerminateTrialById(@QueryParam("aggregate") Boolean aggregate)
			throws SQLException, URISyntaxException, IOException
	{
		if (aggregate == null)
			aggregate = false;

		if (StringUtils.isBlank(shareCode))
			return Response.status(Response.Status.BAD_REQUEST).build();

		try (Connection conn = Database.getConnection())
		{
			DSLContext context = Database.getContext(conn);

			Trials trial = context.selectFrom(TRIALS)
								  .where(TRIALS.OWNER_CODE.eq(shareCode)
														  .or(TRIALS.EDITOR_CODE.eq(shareCode))
														  .or(TRIALS.VIEWER_CODE.eq(shareCode)))
								  .fetchAnyInto(Trials.class);

			if (trial == null)
				return Response.status(Response.Status.NOT_FOUND).build();

			Trial result = trial.getTrial();

			URL resource = PropertyWatcher.class.getClassLoader().getResource("trials-data.xlsx");
			if (resource != null)
			{
				File template = new File(resource.toURI());

				String uuid = UUID.randomUUID().toString();
				File folder = new File(System.getProperty("java.io.tmpdir"), "gridscore-next");
				folder.mkdirs();
				File sourceCopy = new File(folder, "template-" + uuid + ".xlsx");
				Files.copy(template.toPath(), sourceCopy.toPath(), StandardCopyOption.REPLACE_EXISTING);
				File target = new File(folder, uuid + ".xlsx");

				new DataToSpreadsheet(template, target, result, aggregate)
						.run();

				sourceCopy.delete();

				return Response.ok(uuid)
							   .build();
			}
		}

		return Response.noContent().build();
	}

	@GET
	@Path("/g8/{uuid}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	public Response getConfigExportDownload(@PathParam("uuid") String uuid)
			throws IOException, SQLException
	{
		if (StringUtils.isBlank(shareCode) || StringUtils.isBlank(uuid))
			return Response.status(Response.Status.BAD_REQUEST).build();

		try (Connection conn = Database.getConnection())
		{
			DSLContext context = Database.getContext(conn);
			Trials trials = context.selectFrom(TRIALS)
								   .where(TRIALS.OWNER_CODE.eq(shareCode)
														   .or(TRIALS.EDITOR_CODE.eq(shareCode))
														   .or(TRIALS.VIEWER_CODE.eq(shareCode)))
								   .fetchAnyInto(Trials.class);

			if (trials == null)
				return Response.status(Response.Status.NOT_FOUND).build();

			File folder = new File(System.getProperty("java.io.tmpdir"), "gridscore-next");
			File result = new File(folder, uuid + ".xlsx");

			if (!FileUtils.isSubDirectory(folder, result))
				return Response.status(Response.Status.BAD_REQUEST).build();
			if (!result.exists() || !result.isFile())
				return Response.status(Response.Status.NOT_FOUND).build();

			String friendlyFilename = trials.getTrial().getName().replaceAll("\\W+", "-") + "-" + shareCode;

			java.nio.file.Path filePath = result.toPath();
			return Response.ok((StreamingOutput) output -> {
							   Files.copy(filePath, output);
							   Files.deleteIfExists(filePath);
						   })
						   .type("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
						   .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=\"" + friendlyFilename + ".xlsx\"")
						   .header(HttpHeaders.CONTENT_LENGTH, result.length())
						   .build();
		}
	}
}
