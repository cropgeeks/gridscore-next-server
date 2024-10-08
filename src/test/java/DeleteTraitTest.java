import com.google.gson.Gson;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.*;
import jhi.gridscore.server.pojo.*;
import org.junit.jupiter.api.*;

import java.io.InputStreamReader;
import java.util.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DeleteTraitTest extends ConfigTest
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


	/**
	 * Attempt an actual change to the configuration. Check if changes are applied correctly.
	 */
	@Order(2)
	@Test
	void testConfigTransactionDataChange()
	{
		Transaction t = new Transaction();
		t.setTrialTraitDeletedTransactions(Collections.singletonList(trial.getTraits().get(0)));

		ConfigTest.ApiResult<Trial> result = sendTransaction(null, trial.getShareCodes().getOwnerCode(), t);
		Assertions.assertEquals(200, result.status);

		Assertions.assertEquals(result.data.getTraits().size(), trial.getTraits().size() - 1);
	}

	@Order(3)
	@Test
	void testSendingDataForDeletedTrait()
	{
		Transaction t = new Transaction();
		Map<String, List<TraitMeasurement>> changes = new HashMap<>();

		Calendar calendar = new GregorianCalendar();
		calendar.set(2023, Calendar.JULY, 23, 0, 0, 0);
		Date one = calendar.getTime();

		// Categorical, setSize 1, no repeats
		TraitMeasurement m1 = new TraitMeasurement();
		m1.setTraitId(trial.getTraits().get(0).getId());
		m1.setTimestamp(formatDate(one));
		m1.setValues(List.of("0"));
		changes.put("0|0", List.of(m1));

		t.setPlotTraitDataChangeTransactions(changes);

		ApiResult<Trial> result = sendTransaction(null, trial.getShareCodes().getOwnerCode(), t);
		Assertions.assertEquals(200, result.status);
		Assertions.assertEquals(result.data.getTraits().size(), trial.getTraits().size() - 1);
		List<Measurement> measurements = result.data.getData().get("0|0").getMeasurements().get(trial.getTraits().get(0).getId());
		Assertions.assertNull(measurements);
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
