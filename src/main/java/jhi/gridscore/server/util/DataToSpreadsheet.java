package jhi.gridscore.server.util;

import com.google.gson.Gson;
import jhi.gridscore.server.pojo.Cell;
import jhi.gridscore.server.pojo.Comment;
import jhi.gridscore.server.pojo.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.*;
import org.apache.poi.xssf.usermodel.*;
import org.jooq.tools.StringUtils;

import java.io.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.*;

public class DataToSpreadsheet
{
	public static void main(String[] args)
			throws IOException, SQLException
	{
//		File template = new File("C:/Users/sr41756/workspaces/java/gridscore-next-server/src/main/resources/trials-data.xlsx");
//		File target = new File("C:/Users/sr41756/Downloads/trial-export-test.xlsx");
//
//		if (target.exists() && target.isFile())
//			target.delete();
//
//		Database.init("localhost", "gridscore_next", null, "root", null, false);
//
//		try (Connection conn = Database.getConnection())
//		{
//			DSLContext context = Database.getContext(conn);
//
//			Trials trials = context.selectFrom(TRIALS).where(TRIALS.OWNER_CODE.eq("tg2WYLBecHPWsdYxCuz2UWl-76c")).fetchAnyInto(Trials.class);
//
//			new DataToSpreadsheet(template, target, trials.getTrial(), false).run();
//		}
	}

	private File                 template;
	private File                 target;
	private Trial                trial;
	private boolean              aggregate;
	private Map<String, Integer> traitToColumnIndex = new HashMap<>();

	private boolean hasPlotComments = false;

	public DataToSpreadsheet(File template, File target, Trial trial, boolean aggregate)
	{
		this.template = template;
		this.target = target;
		this.trial = trial;
		this.aggregate = aggregate;

		if (trial.getData() != null)
		{
			for (Map.Entry<String, Cell> entry : trial.getData().entrySet())
			{
				if (!CollectionUtils.isEmpty(entry.getValue().getComments()))
				{
					hasPlotComments = true;
					break;
				}
			}
		}
	}

	private String getTimezonedDate(String input, boolean dashes)
	{
		ZonedDateTime offsetTz = ZonedDateTime.parse(input, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX"));

		if (dashes)
			return offsetTz.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		else
			return offsetTz.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
	}

	private String getTimezonedNow()
	{
		return ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
	}

	public void run()
			throws IOException
	{
		try (FileInputStream is = new FileInputStream(template);
			 FileOutputStream os = new FileOutputStream(target))
		{
			XSSFWorkbook workbook = new XSSFWorkbook(is);

			XSSFSheet data = workbook.getSheet("DATA");
			XSSFSheet dates = workbook.getSheet("RECORDING_DATES");

			// Write title and description
			XSSFSheet metadata = workbook.getSheet("METADATA");
			metadata.getRow(1).getCell(2).setCellValue(trial.getName());
			if (!StringUtils.isBlank(trial.getDescription()))
				metadata.getRow(2).getCell(2).setCellValue("GridScore trial: " + trial.getDescription());
			else
				metadata.getRow(2).getCell(2).setCellValue("GridScore trial");
			if (trial.getUpdatedOn() != null)
				metadata.getRow(4).getCell(2).setCellValue(getTimezonedDate(trial.getUpdatedOn(), true));
			else
				metadata.getRow(4).getCell(2).setCellValue(getTimezonedNow());

			XSSFRow dataRow = data.getRow(0);
			XSSFRow dateRow = dates.getRow(0);

			int offset = 10;
			List<Trait> traits = new ArrayList<>(trial.getTraits());
			for (int i = 0; i < trial.getTraits().size(); i++)
			{
				Trait t = trial.getTraits().get(i);

				if (Objects.equals(t.getDataType(), "gps"))
				{
					dataRow.createCell(i + offset).setCellValue(t.getName());
					dateRow.createCell(i + offset).setCellValue(t.getName());
					traitToColumnIndex.put(t.getId(), i + offset);

					// Add two dummy traits to split up lat/lng
					offset++;
					Trait lat = new Trait().setName(t.getName() + "-latitude").setId(t.getId() + "-latitude").setDataType("float").setSetSize(t.getSetSize()).setAllowRepeats(t.isAllowRepeats()).setGroup(t.getGroup());
					dataRow.createCell(i + offset).setCellValue(lat.getName());
					dateRow.createCell(i + offset).setCellValue(lat.getName());
					traitToColumnIndex.put(lat.getId(), i + offset);
					traits.add(lat);

					offset++;
					Trait lng = new Trait().setName(t.getName() + "-longitude").setId(t.getId() + "-longitude").setDataType("float").setSetSize(t.getSetSize()).setAllowRepeats(t.isAllowRepeats()).setGroup(t.getGroup());
					dataRow.createCell(i + offset).setCellValue(lng.getName());
					dateRow.createCell(i + offset).setCellValue(lng.getName());
					traitToColumnIndex.put(lng.getId(), i + offset);
					traits.add(lng);

					setSplitValues(t, lat, lng, trial.getData());
				}
				else
				{
					dataRow.createCell(i + offset).setCellValue(t.getName());
					dateRow.createCell(i + offset).setCellValue(t.getName());
					traitToColumnIndex.put(t.getId(), i + offset);
				}
			}
			trial.setTraits(traits);

			if (hasPlotComments)
			{
				dataRow.createCell(traitToColumnIndex.size() + 10).setCellValue("Plot comment");
				dateRow.createCell(traitToColumnIndex.size() + 10).setCellValue("Plot comment");
			}

			if (aggregate)
				exportAggregatedPerDay(data, dates);
			else
				exportIndividually(data, dates);

			if (!CollectionUtils.isEmpty(trial.getPeople()))
				writeCollaborators(workbook);
			writeTraits(workbook);
			writeAttributes(workbook);

			workbook.setActiveSheet(0);
			workbook.write(os);
			workbook.close();
		}
	}

	private void setSplitValues(Trait gps, Trait lat, Trait lng, Map<String, Cell> data)
	{
		data.values().forEach(c -> {
			if (c == null || !c.getMeasurements().containsKey(gps.getId()))
				return;

			List<Measurement> ms = c.getMeasurements().get(gps.getId());

			c.getMeasurements().put(lat.getId(), ms.stream().map(mm -> new Measurement()
														   .setPersonId(mm.getPersonId())
														   .setTimestamp(mm.getTimestamp())
														   .setValues(mm.getValues().stream().map(v -> {
															   if (StringUtils.isEmpty(v))
																   return v;

															   String[] parts = v.split(";");
															   if (parts.length == 2)
																   return parts[0];
															   else
																   return null;
														   }).collect(Collectors.toList())))
												   .collect(Collectors.toList()));
			c.getMeasurements().put(lng.getId(), ms.stream().map(mm -> new Measurement()
														   .setPersonId(mm.getPersonId())
														   .setTimestamp(mm.getTimestamp())
														   .setValues(mm.getValues().stream().map(v -> {
															   if (StringUtils.isEmpty(v))
																   return v;

															   String[] parts = v.split(";");
															   if (parts.length == 2)
																   return parts[1];
															   else
																   return null;
														   }).collect(Collectors.toList())))
												   .collect(Collectors.toList()));
		});
	}

	private int getRowLabel(int row)
	{
		if (!CollectionUtils.isEmpty(trial.getLayout().getRowLabels()) && trial.getLayout().getRowLabels().size() == trial.getLayout().getRows())
		{
			return trial.getLayout().getRowLabels().get(row);
		}
		else
		{
			if (Objects.equals(trial.getLayout().getRowOrder(), Layout.DISPLAY_ORDER_BOTTOM_TO_TOP))
				return trial.getLayout().getRows() - row;
			else
				return row + 1;
		}
	}

	private int getColumnLabel(int column)
	{
		if (!CollectionUtils.isEmpty(trial.getLayout().getColumnLabels()) && trial.getLayout().getColumnLabels().size() == trial.getLayout().getColumns())
		{
			return trial.getLayout().getColumnLabels().get(column);
		}
		else
		{
			if (Objects.equals(trial.getLayout().getColumnOrder(), Layout.DISPLAY_ORDER_RIGHT_TO_LEFT))
				return trial.getLayout().getColumns() - column;
			else
				return column + 1;
		}
	}

	private void exportIndividually(XSSFSheet data, XSSFSheet dates)
	{
		final int[] sheetRow = { 0 };

		trial.getData().forEach((cellIdentifier, cell) -> {
			String[] rowColumn = cellIdentifier.split("\\|");
			int row = Integer.parseInt(rowColumn[0]);
			int col = Integer.parseInt(rowColumn[1]);

			// Get the maximum number of measurements (including individual set size measurements)
			int[] measurementCount = new int[]{0};
			cell.getMeasurements().forEach((traitId, measurements) -> {
				long count = measurements.stream().map(m -> m.getValues().stream().filter(Objects::nonNull).count()).reduce(0l, Long::sum);
				measurementCount[0] = (int) Math.max(measurementCount[0], count);
			});

			if (!CollectionUtils.isEmpty(cell.getComments()))
			{
				measurementCount[0] = Math.max(measurementCount[0], cell.getComments().size());
			}

			// Write all the metadata for the germplasm (repeated enough times)
			for (int j = 0; j < measurementCount[0]; j++)
			{
				int i = j + sheetRow[0];
				XSSFRow d = data.getRow(i + 1);
				if (d == null)
					d = data.createRow(i + 1);
				XSSFRow p = dates.getRow(i + 1);
				if (p == null)
					p = dates.createRow(i + 1);

				// Write the germplasm name
				XSSFCell dc = getCell(d, 0);
				XSSFCell pc = getCell(p, 0);
				dc.setCellValue(cell.getGermplasm());
				pc.setCellValue(cell.getGermplasm());

				// Write the rep
				dc = getCell(d, 1);
				pc = getCell(p, 1);
				if (!StringUtils.isBlank(cell.getRep()))
				{
					dc.setCellValue(cell.getRep());
					pc.setCellValue(cell.getRep());
				}
				else
				{
					dc.setCellValue("1");
					pc.setCellValue("1");
				}

				int rowLabel = getRowLabel(row);
				int columnLabel = getColumnLabel(col);

				// Write the row
				dc = getCell(d, 3);
				pc = getCell(p, 3);
				dc.setCellValue(rowLabel);
				pc.setCellValue(rowLabel);

				// Write the column
				dc = getCell(d, 4);
				pc = getCell(p, 4);
				dc.setCellValue(columnLabel);
				pc.setCellValue(columnLabel);

				// Write the location
				if (cell.getGeography() != null)
				{
					Double lat = null;
					Double lng = null;
					if (cell.getGeography().getCorners() != null)
					{
						lat = 0d;
						lng = 0d;

						lat += cell.getGeography().getCorners().getTopLeft().getLat();
						lng += cell.getGeography().getCorners().getTopLeft().getLng();
						lat += cell.getGeography().getCorners().getTopRight().getLat();
						lng += cell.getGeography().getCorners().getTopRight().getLng();
						lat += cell.getGeography().getCorners().getBottomRight().getLat();
						lng += cell.getGeography().getCorners().getBottomRight().getLng();
						lat += cell.getGeography().getCorners().getBottomLeft().getLat();
						lng += cell.getGeography().getCorners().getBottomLeft().getLng();

						lat /= 4.0;
						lng /= 4.0;
					}
					else if (cell.getGeography().getCenter() != null)
					{
						lat = cell.getGeography().getCenter().getLat();
						lng = cell.getGeography().getCenter().getLng();
					}

					if (lat != null && lng != null)
					{
						dc = getCell(d, 7);
						dc.setCellValue(lat);
						pc = getCell(p, 7);
						pc.setCellValue(lat);
						dc = getCell(d, 8);
						dc.setCellValue(lng);
						pc = getCell(p, 8);
						pc.setCellValue(lng);
					}
				}
			}

			XSSFCell dc;
			XSSFCell pc;

			for (int ti = 0; ti < trial.getTraits().size(); ti++)
			{
				Trait t = trial.getTraits().get(ti);
				int traitIndex = traitToColumnIndex.get(t.getId());

				List<Measurement> measurements = cell.getMeasurements().get(t.getId());

				if (!CollectionUtils.isEmpty(measurements))
				{
					int counter = 0;
					for (int j = 0; j < measurements.size(); j++)
					{
						List<String> nonNullValues = measurements.get(j).getValues().stream().filter(v -> !StringUtils.isBlank(v)).collect(Collectors.toList());
						for (int v = 0; v < nonNullValues.size(); v++)
						{
							String value = nonNullValues.get(v);

							int i = counter + sheetRow[0] + v;
							XSSFRow d = data.getRow(i + 1);
							XSSFRow p = dates.getRow(i + 1);

							dc = getCell(d, traitIndex);
							pc = getCell(p, traitIndex);

							if (Objects.equals(t.getDataType(), "int") || Objects.equals(t.getDataType(), "numeric"))
							{
								try
								{
									Double.parseDouble(value);
									setCell(t, dc, value);
									setCell(t, pc, getTimezonedDate(measurements.get(j).getTimestamp(), false));
								}
								catch (Exception e)
								{
									// Do nothing here
								}
							}
							else if (Objects.equals(t.getDataType(), "categorical"))
							{
								String parsed = t.getRestrictions().getCategories().get(Integer.parseInt(value));
								setCell(t, dc, parsed);
								setCell(t, pc, getTimezonedDate(measurements.get(j).getTimestamp(), false));
							}
							else
							{
								setCell(t, dc, value);
								setCell(t, pc, getTimezonedDate(measurements.get(j).getTimestamp(), false));
							}
						}
						counter += nonNullValues.size();
					}
				}
			}

			if (!CollectionUtils.isEmpty(cell.getComments()))
			{
				for (int j = 0; j < cell.getComments().size(); j++)
				{
					int i = sheetRow[0] + j;
					XSSFRow d = data.getRow(i + 1);
					XSSFRow p = dates.getRow(i + 1);

					dc = getCell(d, traitToColumnIndex.size() + 10);
					pc = getCell(p, traitToColumnIndex.size() + 10);

					Comment comment = cell.getComments().get(j);
					setCell(new Trait().setDataType("text"), dc, comment.getContent());
					setCell(new Trait().setDataType("date"), pc, getTimezonedDate(comment.getTimestamp(), false));
				}
			}

			sheetRow[0] += measurementCount[0];
		});

//		data.getTables().forEach(data::removeTable);
//		AreaReference area = data.getWorkbook().getCreationHelper().createAreaReference(new CellReference(0, 0), new CellReference(sheetRow[0], trial.getTraits().size() + 10 + (hasPlotComments ? 1 : 0)));
//		XSSFTable table = data.createTable(area);
//		IntStream.of(table.getColumns().size()).forEach(i -> {
//			table.getCTTable().getTableColumns().getTableColumnArray(i - 1).setId(i);
//		});
//		table.updateReferences();
//		table.updateHeaders();
	}

	private void exportAggregatedPerDay(XSSFSheet data, XSSFSheet dates)
	{
		List<CoordinateDateCell> cells = new ArrayList<>();

		trial.getData().forEach((cellIdentifier, cell) -> {
			Set<String> unique = new HashSet<>();
			String[] rowColumn = cellIdentifier.split("\\|");
			int row = Integer.parseInt(rowColumn[0]);
			int col = Integer.parseInt(rowColumn[1]);
			cell.getMeasurements().forEach((traitId, measurements) -> measurements.forEach(m -> {
				String date = getTimezonedDate(m.getTimestamp(), false);

				if (!unique.contains(date))
				{
					cells.add(new CoordinateDateCell(cell, row, col, date));
					unique.add(date);
				}
			}));

			// Account for comments
			if (!CollectionUtils.isEmpty(cell.getComments()))
			{
				cell.getComments().forEach(c -> {
					String date = getTimezonedDate(c.getTimestamp(), false);

					if (!unique.contains(date))
					{
						cells.add(new CoordinateDateCell(cell, row, col, date));
						unique.add(date);
					}
				});
			}
		});

		IntStream.range(0, cells.size())
				 .forEach(i -> {
					 XSSFRow d = data.getRow(i + 1);
					 if (d == null)
						 d = data.createRow(i + 1);
					 XSSFRow p = dates.getRow(i + 1);
					 if (p == null)
						 p = dates.createRow(i + 1);

					 CoordinateDateCell c = cells.get(i);
					 // Write the germplasm name
					 XSSFCell dc = getCell(d, 0);
					 XSSFCell pc = getCell(p, 0);
					 dc.setCellValue(c.getGermplasm());
					 pc.setCellValue(c.getGermplasm());

					 // Write the rep
					 dc = getCell(d, 1);
					 pc = getCell(p, 1);
					 if (!StringUtils.isBlank(c.getRep()))
					 {
						 dc.setCellValue(c.getRep());
						 pc.setCellValue(c.getRep());
					 }
					 else
					 {
						 dc.setCellValue("1");
						 pc.setCellValue("1");
					 }

					 int rowLabel = getRowLabel(c.row);
					 int columnLabel = getColumnLabel(c.col);

					 // Write the row
					 dc = getCell(d, 3);
					 pc = getCell(p, 3);
					 dc.setCellValue(rowLabel);
					 pc.setCellValue(rowLabel);

					 // Write the column
					 dc = getCell(d, 4);
					 pc = getCell(p, 4);
					 dc.setCellValue(columnLabel);
					 pc.setCellValue(columnLabel);

					 // Write the location
					 if (c.getGeography() != null)
					 {
						 Double lat = null;
						 Double lng = null;
						 if (c.getGeography().getCorners() != null)
						 {
							 lat = 0d;
							 lng = 0d;

							 lat += c.getGeography().getCorners().getTopLeft().getLat();
							 lng += c.getGeography().getCorners().getTopLeft().getLng();
							 lat += c.getGeography().getCorners().getTopRight().getLat();
							 lng += c.getGeography().getCorners().getTopRight().getLng();
							 lat += c.getGeography().getCorners().getBottomRight().getLat();
							 lng += c.getGeography().getCorners().getBottomRight().getLng();
							 lat += c.getGeography().getCorners().getBottomLeft().getLat();
							 lng += c.getGeography().getCorners().getBottomLeft().getLng();

							 lat /= 4.0;
							 lng /= 4.0;
						 }
						 else if (c.getGeography().getCenter() != null)
						 {
							 lat = c.getGeography().getCenter().getLat();
							 lng = c.getGeography().getCenter().getLng();
						 }

						 if (lat != null && lng != null)
						 {
							 dc = getCell(d, 7);
							 dc.setCellValue(lat);
							 pc = getCell(p, 7);
							 pc.setCellValue(lat);
							 dc = getCell(d, 8);
							 dc.setCellValue(lng);
							 pc = getCell(p, 8);
							 pc.setCellValue(lng);
						 }
					 }


					 for (int j = 0; j < trial.getTraits().size(); j++)
					 {
						 Trait t = trial.getTraits().get(j);

						 int traitIndex = traitToColumnIndex.get(t.getId());

						 dc = getCell(d, traitIndex);
						 pc = getCell(p, traitIndex);

						 List<Measurement> traitMeasurements = c.getMeasurements().get(t.getId());
						 List<Measurement> measurements = traitMeasurements == null ? new ArrayList<>() : traitMeasurements.stream().filter(m -> {
							 String date = getTimezonedDate(m.getTimestamp(), false);
							 return Objects.equals(date, c.date);
						 }).collect(Collectors.toList());

						 if (CollectionUtils.isEmpty(measurements))
						 {
							 setCell(t, dc, null);
							 setCell(t, pc, null);
							 continue;
						 }

						 String value;
						 if (Objects.equals(t.getDataType(), "int") || Objects.equals(t.getDataType(), "numeric"))
						 {
							 double total = 0;
							 int count = 0;

							 for (Measurement m : measurements)
							 {
								 for (String v : m.getValues())
								 {
									 try
									 {
										 total += Double.parseDouble(v);
										 count++;
									 }
									 catch (NumberFormatException | NullPointerException e)
									 {
										 // Ignore
									 }
								 }
							 }

							 if (count > 0)
								 value = Double.toString(total / count);
							 else
								 value = null;
						 }
						 else
						 {
							 List<String> values = measurements.get(measurements.size() - 1).getValues();
							 value = values.get(values.size() - 1);

							 if (Objects.equals(t.getDataType(), "categorical"))
								 value = t.getRestrictions().getCategories().get(Integer.parseInt(value));
						 }

						 setCell(t, dc, value);
						 if (value != null)
						 {
							 setCell(t, pc, c.date);
						 }
					 }

					 if (!CollectionUtils.isEmpty(c.getComments()))
					 {
						 List<Comment> comments = c.getComments().stream().filter(m -> {
							 String date = getTimezonedDate(m.getTimestamp(), false);
							 return Objects.equals(date, c.date);
						 }).collect(Collectors.toList());

						 if (!CollectionUtils.isEmpty(comments))
						 {
							 dc = getCell(d, traitToColumnIndex.size() + 10);
							 pc = getCell(p, traitToColumnIndex.size() + 10);

							 setCell(new Trait().setDataType("text"), dc, comments.stream().map(Comment::getContent).collect(Collectors.joining("; ")));
							 setCell(new Trait().setDataType("date"), pc, c.date);
						 }
					 }
				 });
	}

	private XSSFCell getCell(XSSFRow row, int index)
	{
		XSSFCell cell = row.getCell(index);
		if (cell == null)
			cell = row.createCell(index);
		return cell;
	}

	private void writeCollaborators(XSSFWorkbook workbook)
	{
		XSSFSheet collaborators = workbook.getSheet("COLLABORATORS");
		XSSFTable collaboratorsTable = collaborators.getTables().get(0);

		// Adjust the table size
		AreaReference area = new AreaReference(collaboratorsTable.getStartCellReference(), new CellReference(trial.getPeople().size() + 1, collaboratorsTable.getEndCellReference().getCol()), SpreadsheetVersion.EXCEL2007);
		collaboratorsTable.setArea(area);
		collaboratorsTable.getCTTable().getAutoFilter().setRef(area.formatAsString());
		collaboratorsTable.updateReferences();

		final XSSFSheet sheet = collaboratorsTable.getXSSFSheet();

		// Write collection method
		int i = 1;
		XSSFRow row;

		for (Person p : trial.getPeople())
		{
			i++;
			row = sheet.getRow(i);
			if (row == null)
				row = sheet.createRow(i);

			row.createCell(1).setCellValue(p.getName());
			row.createCell(2).setCellValue(p.getTypes().stream().filter(Objects::nonNull).map(Person.PersonType::getTemplateName).collect(Collectors.joining(";")));
			if (!StringUtils.isBlank(p.getEmail()))
				row.createCell(4).setCellValue(p.getEmail());
		}
	}

	private void writeAttributes(XSSFWorkbook workbook)
	{
		XSSFSheet attributes = workbook.getSheet("ATTRIBUTES");
		XSSFTable attributeTable = attributes.getTables().get(0);

		int count = 3;
		if (!CollectionUtils.isEmpty(trial.getComments()))
			count++;
		if (!StringUtils.isEmpty(trial.getBrapiId()))
			count++;

		// Adjust the table size
		AreaReference area = new AreaReference(attributeTable.getStartCellReference(), new CellReference(count, attributeTable.getEndCellReference().getCol()), SpreadsheetVersion.EXCEL2007);
		attributeTable.setArea(area);
		attributeTable.getCTTable().getAutoFilter().setRef(area.formatAsString());
		attributeTable.updateReferences();

		final XSSFSheet sheet = attributeTable.getXSSFSheet();

		// Write collection method
		int i = 0;
		XSSFRow row;

		if (!StringUtils.isEmpty(trial.getBrapiId()))
		{
			i++;
			row = sheet.getRow(i);
			if (row == null)
				row = sheet.createRow(i);
			row.createCell(0).setCellValue("BrAPI Study DB ID");
			row.createCell(1).setCellValue("text");
			row.createCell(2).setCellValue(trial.getBrapiId());
		}

		i++;
		row = sheet.getRow(i);
		if (row == null)
			row = sheet.createRow(i);
		row.createCell(0).setCellValue("Collection method");
		row.createCell(1).setCellValue("text");
		row.createCell(2).setCellValue("Mobile app");

		// Write app name
		i++;
		row = sheet.getRow(i);
		if (row == null)
			row = sheet.createRow(i);
		row.createCell(0).setCellValue("Collection app name");
		row.createCell(1).setCellValue("text");
		row.createCell(2).setCellValue("GridScore");

		// Write app version
		i++;
		row = sheet.getRow(i);
		if (row == null)
			row = sheet.createRow(i);
		row.createCell(0).setCellValue("Collection app version");
		row.createCell(1).setCellValue("text");
		Package pkg = getClass().getPackage();
		if (pkg != null && !StringUtils.isBlank(pkg.getImplementationVersion()))
			row.createCell(2).setCellValue(pkg.getImplementationVersion());
		else
			row.createCell(2).setCellValue("DEVELOPMENT");

		if (!CollectionUtils.isEmpty(trial.getComments()))
		{
			// Write the trial comments
			i++;
			row = sheet.getRow(i);
			if (row == null)
				row = sheet.createRow(i);
			row.createCell(0).setCellValue("Trial comments");
			row.createCell(1).setCellValue("text");
			XSSFCell cell = row.createCell(2);
			cell.setCellValue(trial.getComments()
								   .stream()
								   .filter(c -> !StringUtils.isBlank(c.getContent()))
								   .map(c -> getTimezonedDate(c.getTimestamp(), true) + ": " + c.getContent().replaceAll("\r?\n", " "))
								   .collect(Collectors.joining("\n")));

			// Allow wrapping on new line characters
			CellStyle cs = workbook.createCellStyle();
			cs.setWrapText(true);
			cell.setCellStyle(cs);
		}

		if (!CollectionUtils.isEmpty(trial.getEvents()))
		{
			// Write the trial events
			i++;
			row = sheet.getRow(i);
			if (row == null)
				row = sheet.createRow(i);
			row.createCell(0).setCellValue("Trial events");
			row.createCell(1).setCellValue("text");
			XSSFCell cell = row.createCell(2);
			cell.setCellValue(trial.getEvents()
								   .stream()
								   .filter(e -> !StringUtils.isBlank(e.getContent()))
								   .map(e -> getTimezonedDate(e.getTimestamp(), true) + " (" + e.getType() + "; " + e.getImpact() + "): " + e.getContent().replaceAll("\r?\n", " "))
								   .collect(Collectors.joining("\n")));

			// Allow wrapping on new line characters
			CellStyle cs = workbook.createCellStyle();
			cs.setWrapText(true);
			cell.setCellStyle(cs);
		}
	}

	private void writeTraits(XSSFWorkbook workbook)
	{
		Gson gson = new Gson();

		XSSFSheet phenotypes = workbook.getSheet("PHENOTYPES");
		XSSFTable traitTable = phenotypes.getTables().get(0);

		int traitDimensions = trial.getTraits().size();

		if (hasPlotComments)
			traitDimensions++;

		// Adjust the table size
		AreaReference area = new AreaReference(traitTable.getStartCellReference(), new CellReference(traitDimensions, traitTable.getEndCellReference().getCol()), SpreadsheetVersion.EXCEL2007);
		traitTable.setArea(area);
		traitTable.getCTTable().getAutoFilter().setRef(area.formatAsString());
		traitTable.updateReferences();

		final XSSFSheet sheet = traitTable.getXSSFSheet();

		IntStream.range(0, trial.getTraits().size())
				 .forEach(i -> {
					 Trait t = trial.getTraits().get(i);
					 XSSFRow row = sheet.getRow(i + 1);

					 if (row == null)
						 row = sheet.createRow(i + 1);

					 row.createCell(0).setCellValue(t.getName());
					 if (!StringUtils.isBlank(t.getDescription()))
						 row.createCell(2).setCellValue(t.getDescription());
					 switch (t.getDataType())
					 {
						 case "int":
						 case "float":
							 row.createCell(3).setCellValue("numeric");
							 break;
						 case "date":
						 case "text":
						 case "categorical":
							 row.createCell(3).setCellValue(t.getDataType());
							 break;
						 default:
							 row.createCell(3).setCellValue("text");
							 break;
					 }
					 if (t.getRestrictions() != null)
					 {
						 if (!CollectionUtils.isEmpty(t.getRestrictions().getCategories()))
							 row.createCell(7).setCellValue("[" + gson.toJson(t.getRestrictions().getCategories()) + "]");
						 if (t.getRestrictions().getMin() != null)
							 row.createCell(8).setCellValue(t.getRestrictions().getMin());
						 if (t.getRestrictions().getMax() != null)
							 row.createCell(9).setCellValue(t.getRestrictions().getMax());
					 }

					 row.createCell(10).setCellValue(t.getSetSize());
					 row.createCell(11).setCellValue(t.isAllowRepeats() ? "true" : "false");
				 });

		if (hasPlotComments)
		{
			XSSFRow row = sheet.getRow(traitDimensions);

			if (row == null)
				row = sheet.createRow(traitDimensions);

			row.createCell(0).setCellValue("Plot comment");
			row.createCell(2).setCellValue("Comments provided from the data collectors");
			row.createCell(3).setCellValue("text");
			row.createCell(10).setCellValue(1);
			row.createCell(11).setCellValue("true");
		}
	}

	private void setCell(Trait t, XSSFCell cell, String value)
	{
		if (Objects.equals(t.getDataType(), "date"))
			cell.setCellType(CellType.STRING);
		cell.setCellValue(value);
	}

	private static class CoordinateCell extends Cell
	{
		protected int row;
		protected int col;

		public CoordinateCell(Cell original, int row, int col)
		{
			super(original);
			this.row = row;
			this.col = col;
		}
	}

	private static class CoordinateDateCell extends CoordinateCell
	{
		protected String date;

		public CoordinateDateCell(Cell original, int row, int col, String date)
		{
			super(original, row, col);
			this.date = date;
		}
	}
}
