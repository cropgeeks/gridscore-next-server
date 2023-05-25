package jhi.gridscore.server.util;

import com.google.gson.Gson;
import jhi.gridscore.server.database.Database;
import jhi.gridscore.server.database.codegen.tables.pojos.Trials;
import jhi.gridscore.server.pojo.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.*;
import org.apache.poi.xssf.usermodel.*;
import org.jooq.DSLContext;
import org.jooq.tools.StringUtils;

import java.io.*;
import java.sql.*;
import java.text.ParseException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.*;

import static jhi.gridscore.server.database.codegen.tables.Trials.TRIALS;

public class DataToSpreadsheet
{
	public static void main(String[] args)
			throws IOException, SQLException
	{
		File template = new File("C:/Users/sr41756/workspaces/java/gridscore-next/src/main/resources/trials-data.xlsx");
		File target = new File("C:/Users/sr41756/Downloads/trial-export-test.xlsx");

		if (target.exists() && target.isFile())
			target.delete();

		Database.init("localhost", "gridscore-next", null, "root", null, false);

		try (Connection conn = Database.getConnection())
		{
			DSLContext context = Database.getContext(conn);

			Trials trials = context.selectFrom(TRIALS).where(TRIALS.OWNER_CODE.eq("0GAtAzTDdaJ9F2sGd1YAnEYqWfI")).fetchAnyInto(Trials.class);

			new DataToSpreadsheet(template, target, trials.getTrial()).run();
		}
	}

	private File           template;
	private File           target;
	private Trial          trial;

	public DataToSpreadsheet(File template, File target, Trial trial)
	{
		this.template = template;
		this.target = target;
		this.trial = trial;
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

			writeTraits(workbook);

			writeAttributes(workbook);

			XSSFRow dataRow = data.getRow(0);
			XSSFRow dateRow = dates.getRow(0);
			IntStream.range(0, trial.getTraits().size())
					 .forEach(i -> {
						 Trait t = trial.getTraits().get(i);
						 dataRow.createCell(i + 10).setCellValue(t.getName());
						 dateRow.createCell(i + 10).setCellValue(t.getName());
					 });

			exportNonAggregated(data, dates);

			workbook.setActiveSheet(0);
			workbook.write(os);
			workbook.close();
		}
	}

	private void exportNonAggregated(XSSFSheet data, XSSFSheet dates)
	{
		Gson gson = new Gson();

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

					 // Write the row
					 dc = getCell(d, 3);
					 pc = getCell(p, 3);
					 dc.setCellValue(c.row + 1);
					 pc.setCellValue(c.row + 1);

					 // Write the column
					 dc = getCell(d, 4);
					 pc = getCell(p, 4);
					 dc.setCellValue(c.col + 1);
					 pc.setCellValue(c.col + 1);

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

						 dc = getCell(d, j + 10);
						 pc = getCell(p, j + 10);

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
						 }

						 setCell(t, dc, value);
						 if (value != null)
						 {
							 setCell(t, pc, c.date);
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

	private void writeAttributes(XSSFWorkbook workbook)
	{
		XSSFSheet attributes = workbook.getSheet("ATTRIBUTES");
		XSSFTable attributeTable = attributes.getTables().get(0);

		// Adjust the table size
		AreaReference area = new AreaReference(attributeTable.getStartCellReference(), new CellReference(3, attributeTable.getEndCellReference().getCol()), SpreadsheetVersion.EXCEL2007);
		attributeTable.setArea(area);
		attributeTable.getCTTable().getAutoFilter().setRef(area.formatAsString());
		attributeTable.updateReferences();

		final XSSFSheet sheet = attributeTable.getXSSFSheet();

		XSSFRow row = sheet.getRow(1);
		if (row == null)
			row = sheet.createRow(1);
		row.createCell(0).setCellValue("Collection method");
		row.createCell(1).setCellValue("text");
		row.createCell(2).setCellValue("Mobile app");

		row = sheet.getRow(2);
		if (row == null)
			row = sheet.createRow(2);
		row.createCell(0).setCellValue("Collection app name");
		row.createCell(1).setCellValue("text");
		row.createCell(2).setCellValue("GridScore");

		row = sheet.getRow(3);
		if (row == null)
			row = sheet.createRow(3);

		row.createCell(0).setCellValue("Collection app version");
		row.createCell(1).setCellValue("text");

		Package pkg = getClass().getPackage();

		if (pkg != null && !StringUtils.isBlank(pkg.getImplementationVersion()))
		{
			row.createCell(2).setCellValue(pkg.getImplementationVersion());
		}
		else
		{
			row.createCell(2).setCellValue("DEVELOPMENT");
		}
	}

	private void writeTraits(XSSFWorkbook workbook)
	{
		XSSFSheet phenotypes = workbook.getSheet("PHENOTYPES");
		XSSFTable traitTable = phenotypes.getTables().get(0);

		// Adjust the table size
		AreaReference area = new AreaReference(traitTable.getStartCellReference(), new CellReference(trial.getTraits().size(), traitTable.getEndCellReference().getCol()), SpreadsheetVersion.EXCEL2007);
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
							 row.createCell(7).setCellValue("[[" + String.join(",", t.getRestrictions().getCategories()) + "]]");
						 if (t.getRestrictions().getMin() != null)
							 row.createCell(8).setCellValue(t.getRestrictions().getMin());
						 if (t.getRestrictions().getMax() != null)
							 row.createCell(9).setCellValue(t.getRestrictions().getMax());
					 }
				 });
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
