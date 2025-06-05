import com.google.gson.Gson;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.*;
import jhi.gridscore.server.pojo.*;
import jhi.gridscore.server.pojo.transaction.*;
import org.junit.jupiter.api.*;

import java.io.InputStreamReader;
import java.util.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PlotMetadataTest extends ConfigTest
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
	void shareInitialConfig()
			throws Exception
	{
		ConfigTest.ApiResult<Trial> result = sendTrial(null, trial);
		Assertions.assertEquals(200, result.status);
		trial = result.data;
		Assertions.assertNotNull(trial.getShareCodes().getOwnerCode());
	}

	@Order(2)
	@Test
	void testMetadataChange()
	{
		Transaction t = new Transaction();
		Map<String, PlotDetailContent> changes = new HashMap<>();

		changes.put("0|0", new PlotDetailContent()
				.setBarcode("b00")
				.setPedigree("p00")
				.setFriendlyName("fn00")
				.setTreatment("t00"));
		changes.put("0|1", new PlotDetailContent()
				.setBarcode("b01"));
		changes.put("0|2", new PlotDetailContent()
				.setPedigree("p02"));
		changes.put("0|3", new PlotDetailContent()
				.setFriendlyName("fn03"));
		changes.put("0|4", new PlotDetailContent()
				.setTreatment("t04"));

		t.setPlotDetailsChangeTransaction(changes);

		ApiResult<Trial> result = sendTransaction(null, trial.getShareCodes().getOwnerCode(), t);
		Assertions.assertEquals(200, result.status);

		Cell cell = result.data.getData().get("0|0");
		Assertions.assertEquals("b00", cell.getBarcode());
		Assertions.assertEquals("p00", cell.getPedigree());
		Assertions.assertEquals("fn00", cell.getFriendlyName());
		Assertions.assertEquals("t00", cell.getTreatment());

		cell = result.data.getData().get("0|1");
		Assertions.assertEquals("b01", cell.getBarcode());
		Assertions.assertNull(cell.getPedigree());
		Assertions.assertNull(cell.getFriendlyName());
		Assertions.assertNull(cell.getTreatment());

		cell = result.data.getData().get("0|2");
		Assertions.assertNull(cell.getBarcode());
		Assertions.assertEquals("p02", cell.getPedigree());
		Assertions.assertNull(cell.getFriendlyName());
		Assertions.assertNull(cell.getTreatment());

		cell = result.data.getData().get("0|3");
		Assertions.assertNull(cell.getBarcode());
		Assertions.assertNull(cell.getPedigree());
		Assertions.assertEquals("fn03", cell.getFriendlyName());
		Assertions.assertNull(cell.getTreatment());

		cell = result.data.getData().get("0|4");
		Assertions.assertNull(cell.getBarcode());
		Assertions.assertNull(cell.getPedigree());
		Assertions.assertNull(cell.getFriendlyName());
		Assertions.assertEquals("t04", cell.getTreatment());
	}

	@Order(3)
	@Test
	void testMetadataRemoval()
	{
		Transaction t = new Transaction();
		Map<String, PlotDetailContent> changes = new HashMap<>();

		changes.put("0|0", new PlotDetailContent());
		changes.put("0|1", new PlotDetailContent());
		changes.put("0|2", new PlotDetailContent());
		changes.put("0|3", new PlotDetailContent());
		changes.put("0|4", new PlotDetailContent());

		t.setPlotDetailsChangeTransaction(changes);

		ApiResult<Trial> result = sendTransaction(null, trial.getShareCodes().getOwnerCode(), t);
		Assertions.assertEquals(200, result.status);

		Cell cell = result.data.getData().get("0|0");
		Assertions.assertNull(cell.getBarcode());
		Assertions.assertNull(cell.getPedigree());
		Assertions.assertNull(cell.getFriendlyName());
		Assertions.assertNull(cell.getTreatment());

		cell = result.data.getData().get("0|1");
		Assertions.assertNull(cell.getBarcode());
		Assertions.assertNull(cell.getPedigree());
		Assertions.assertNull(cell.getFriendlyName());
		Assertions.assertNull(cell.getTreatment());

		cell = result.data.getData().get("0|2");
		Assertions.assertNull(cell.getBarcode());
		Assertions.assertNull(cell.getPedigree());
		Assertions.assertNull(cell.getFriendlyName());
		Assertions.assertNull(cell.getTreatment());

		cell = result.data.getData().get("0|3");
		Assertions.assertNull(cell.getBarcode());
		Assertions.assertNull(cell.getPedigree());
		Assertions.assertNull(cell.getFriendlyName());
		Assertions.assertNull(cell.getTreatment());

		cell = result.data.getData().get("0|4");
		Assertions.assertNull(cell.getBarcode());
		Assertions.assertNull(cell.getPedigree());
		Assertions.assertNull(cell.getFriendlyName());
		Assertions.assertNull(cell.getTreatment());
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
