import com.google.gson.Gson;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.*;
import jhi.gridscore.server.pojo.Trial;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jooq.tools.StringUtils;
import org.junit.jupiter.api.*;

import java.io.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GerminateExportTest extends ConfigTest
{
	private static Trial trial;
	private static Gson  gson = new Gson();

	/**
	 * Create the initial configuration.
	 */
	@BeforeAll
	static void setUp()
			throws Exception
	{
		try (InputStreamReader is = new InputStreamReader(GerminateExportTest.class.getResourceAsStream("barley.json")))
		{
			trial = gson.fromJson(is, Trial.class);
			setUpClient(null);
		}
	}

	/**
	 * Try sharing it initially.
	 */
	@Order(1)
	@Test
	void shareConfig()
			throws Exception
	{
		ApiResult<Trial> result = sendTrial(null, trial);
		Assertions.assertEquals(200, result.status);
		trial = result.data;
		Assertions.assertNotNull(trial.getShareCodes().getOwnerCode());
	}

	@Order(2)
	@Test
	void exportToGerminate()
			throws Exception
	{
		Response response = client.target(URL)
								  .path(trial.getShareCodes().getOwnerCode() + "/export/g8")
								  .request(MediaType.APPLICATION_JSON)
								  .get();
		Assertions.assertEquals(200, response.getStatus());
		String exportUuid = response.readEntity(String.class);
		Assertions.assertFalse(StringUtils.isEmpty(exportUuid));

		response = client.target(URL)
						 .path(trial.getShareCodes().getOwnerCode() + "/export/g8/" + exportUuid)
						 .request("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
						 .get();
		Assertions.assertEquals(200, response.getStatus());
		InputStream in = response.readEntity(InputStream.class);

		XSSFWorkbook workbook = new XSSFWorkbook(in);
		Assertions.assertEquals(12, workbook.getNumberOfSheets());
		// Check metadata
		Sheet metadata = workbook.getSheet("METADATA");
		Assertions.assertNotNull(metadata);
		String datasetName = metadata.getRow(1).getCell(2).getStringCellValue();
		Assertions.assertEquals(trial.getName(), datasetName);

		// Check traits
		Sheet traits = workbook.getSheet("PHENOTYPES");
		Assertions.assertNotNull(traits);
		Assertions.assertEquals(7, traits.getPhysicalNumberOfRows());
		String traitName = traits.getRow(1).getCell(0).getStringCellValue();
		Assertions.assertEquals("Awn tipping", traitName);

		// Check data
		Sheet data = workbook.getSheet("DATA");
		Assertions.assertNotNull(data);
		Assertions.assertEquals(225, data.getPhysicalNumberOfRows());
		Assertions.assertEquals(16, data.getRow(0).getPhysicalNumberOfCells());
		String value = data.getRow(31).getCell(13).getStringCellValue();
		Assertions.assertEquals("74.5", value);

		// Check dates
		Sheet dates = workbook.getSheet("RECORDING_DATES");
		Assertions.assertNotNull(dates);
		Assertions.assertEquals(225, dates.getPhysicalNumberOfRows());
		Assertions.assertEquals(16, dates.getRow(0).getPhysicalNumberOfCells());
		value = dates.getRow(1).getCell(12).getStringCellValue();
		Assertions.assertEquals("20200801", value);

		workbook.close();
		in.close();
	}

	/**
	 * Remove the configuration from the database again.
	 */
	@AfterAll
	static void breakDown()
			throws Exception
	{
		WebTarget target = client.target(URL)
								 .path(trial.getShareCodes().getOwnerCode())
								 .queryParam("name", trial.getName());

		Response resp = target.request(MediaType.APPLICATION_JSON)
							  .delete();

		Assertions.assertEquals(200, resp.getStatus());
	}
}
