import com.google.gson.Gson;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.*;
import jhi.gridscore.server.pojo.*;
import jhi.gridscore.server.pojo.transaction.TrialContent;
import org.junit.jupiter.api.*;

import java.io.InputStreamReader;
import java.util.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PermissionsTest extends ConfigTest
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
		try (InputStreamReader is = new InputStreamReader(GerminateExportTest.class.getResourceAsStream("minimal.json")))
		{
			trial = gson.fromJson(is, Trial.class);
			setUpClient();
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
		ConfigTest.ApiResult<Trial> result = sendTrial(trial);
		Assertions.assertEquals(200, result.status);
		trial = result.data;
		Assertions.assertNotNull(trial.getShareCodes().getOwnerCode());
	}

	@Order(2)
	@Test
	void testDataChangeAsViewer()
	{
		Transaction t = new Transaction();
		Map<String, List<TraitMeasurement>> changes = new HashMap<>();

		Calendar calendar = new GregorianCalendar();
		calendar.set(2023, Calendar.JULY, 23, 0, 0, 0);
		Date one = calendar.getTime();
		calendar.set(2023, Calendar.JULY, 24, 0, 0, 0);
		Date two = calendar.getTime();
		calendar.set(2023, Calendar.JULY, 25, 0, 0, 0);
		Date three = calendar.getTime();

		TraitMeasurement m1 = new TraitMeasurement();
		m1.setTraitId(trial.getTraits().get(0).getId());
		m1.setTimestamp(formatDate(one));
		m1.setValues(List.of("0"));
		TraitMeasurement m2 = new TraitMeasurement();
		m2.setTraitId(trial.getTraits().get(1).getId());
		m2.setTimestamp(formatDate(two));
		m2.setValues(List.of("3"));
		TraitMeasurement m3 = new TraitMeasurement();
		m3.setTraitId(trial.getTraits().get(1).getId());
		m3.setTimestamp(formatDate(three));
		m3.setValues(List.of("4"));
		changes.put("0|0", Arrays.asList(m1, m2, m3));

		t.setPlotTraitDataChangeTransactions(changes);

		ApiResult<Trial> result = sendTransaction(trial.getShareCodes().getViewerCode(), t);
		Assertions.assertEquals(403, result.status);
	}

	@Order(3)
	@Test
	void testEditTrialAsEditor()
	{
		Transaction t = new Transaction();

		t.setTrialEditTransaction(new TrialContent()
										  .setName("TEST"));

		ApiResult<Trial> result = sendTransaction(trial.getShareCodes().getEditorCode(), t);
		Assertions.assertEquals(403, result.status);
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
