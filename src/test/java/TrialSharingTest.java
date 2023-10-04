import com.google.gson.Gson;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.*;
import jhi.gridscore.server.pojo.*;
import jhi.gridscore.server.pojo.transaction.*;
import org.junit.jupiter.api.*;

import java.io.InputStreamReader;
import java.util.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TrialSharingTest extends ConfigTest
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
		ApiResult<Trial> result = sendTrial(trial);
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
		Map<String, List<TraitMeasurement>> changes = new HashMap<>();

		Calendar calendar = new GregorianCalendar();
		calendar.set(2023, Calendar.JULY, 23, 0, 0, 0);
		Date one = calendar.getTime();
		calendar.set(2023, Calendar.JULY, 24, 0, 0, 0);
		Date two = calendar.getTime();
		calendar.set(2023, Calendar.JULY, 25, 0, 0, 0);
		Date three = calendar.getTime();
		calendar.set(2023, Calendar.JULY, 26, 0, 0, 0);
		Date four = calendar.getTime();

		// Categorical, setSize 1, no repeats
		TraitMeasurement m1 = new TraitMeasurement();
		m1.setTraitId(trial.getTraits().get(0).getId());
		m1.setTimestamp(formatDate(one));
		m1.setValues(List.of("0"));
		// Int, setSize 1, repeats
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

		ApiResult<Trial> result = sendTransaction(trial.getShareCodes().getOwnerCode(), t);
		Assertions.assertEquals(200, result.status);

		Cell cell = result.data.getData().get("0|0");
		Assertions.assertEquals(1, cell.getMeasurements().get(trial.getTraits().get(0).getId()).size());
		Assertions.assertEquals(2, cell.getMeasurements().get(trial.getTraits().get(1).getId()).size());
		Assertions.assertEquals("0", cell.getMeasurements().get(trial.getTraits().get(0).getId()).get(0).getValues().get(0));
		Assertions.assertEquals("3", cell.getMeasurements().get(trial.getTraits().get(1).getId()).get(0).getValues().get(0));
		Assertions.assertEquals("4", cell.getMeasurements().get(trial.getTraits().get(1).getId()).get(1).getValues().get(0));
		trial = result.data;
	}

	@Order(3)
	@Test
	void testConfigTransactionAddComments()
	{
		Transaction t = new Transaction();

		Calendar calendar = new GregorianCalendar();
		calendar.set(2023, Calendar.JULY, 25, 0, 0, 0);
		Date one = calendar.getTime();
		calendar.set(2023, Calendar.JULY, 26, 0, 0, 0);
		Date two = calendar.getTime();

		t.setTrialCommentAddedTransactions(Arrays.asList(
				new TrialCommentContent().setTimestamp(formatDate(one)).setContent("Trial comment 1"),
				new TrialCommentContent().setTimestamp(formatDate(two)).setContent("Trial comment 2")
		));

		Map<String, List<PlotCommentContent>> plotComments = new HashMap<>();
		plotComments.put("1|0", Arrays.asList(
				new PlotCommentContent().setTimestamp(formatDate(one)).setContent("Plot comment 1"),
				new PlotCommentContent().setTimestamp(formatDate(two)).setContent("Plot comment 2")
		));
		t.setPlotCommentAddedTransactions(plotComments);

		ApiResult<Trial> result = sendTransaction(trial.getShareCodes().getOwnerCode(), t);
		Assertions.assertEquals(200, result.status);
		Assertions.assertNotNull(result.data.getComments());
		Assertions.assertEquals(2, result.data.getComments().size());
		Assertions.assertEquals("Trial comment 1", result.data.getComments().get(0).getContent());
		Assertions.assertEquals(formatDate(one), result.data.getComments().get(0).getTimestamp());

		Cell cell = result.data.getData().get("1|0");
		Assertions.assertNotNull(cell.getComments());
		Assertions.assertEquals(2, cell.getComments().size());
		Assertions.assertEquals("Plot comment 2", cell.getComments().get(1).getContent());
		Assertions.assertEquals(formatDate(two), cell.getComments().get(1).getTimestamp());

		trial = result.data;

		t = new Transaction();

		t.setTrialCommentDeletedTransactions(Collections.singletonList(
				new TrialCommentContent().setTimestamp(formatDate(one)).setContent("Trial comment 1")
		));

		plotComments = new HashMap<>();
		plotComments.put("1|0", Collections.singletonList(
				new PlotCommentContent().setTimestamp(formatDate(two)).setContent("Plot comment 2")
		));
		t.setPlotCommentDeletedTransactions(plotComments);

		result = sendTransaction(trial.getShareCodes().getOwnerCode(), t);
		Assertions.assertEquals(200, result.status);
		Assertions.assertNotNull(result.data.getComments());
		Assertions.assertEquals(1, result.data.getComments().size());
		Assertions.assertEquals("Trial comment 2", result.data.getComments().get(0).getContent());
		Assertions.assertEquals(formatDate(two), result.data.getComments().get(0).getTimestamp());

		cell = result.data.getData().get("1|0");
		Assertions.assertNotNull(cell.getComments());
		Assertions.assertEquals(1, cell.getComments().size());
		Assertions.assertEquals("Plot comment 1", cell.getComments().get(0).getContent());
		Assertions.assertEquals(formatDate(one), cell.getComments().get(0).getTimestamp());

		trial = result.data;
	}

	@Order(4)
	@Test
	void testComplexTransaction()
	{
		// We're adding three traits and recording data for old and new traits
		Transaction t = gson.fromJson("{\"plotTraitDataChangeTransactions\":{\"0|0\":[{\"traitId\":\"YTcnavPoi2d3EaKF\",\"values\":[0],\"timestamp\":\"2023-07-24T14:17:45.852Z\"},{\"traitId\":\"DjDDgY-kKUksO90K\",\"values\":[\"4\"],\"timestamp\":\"2023-07-24T14:17:45.852Z\"},{\"traitId\":\"jItYsM6DUTHh_PQB\",\"values\":[\"1\",\"2\",\"3\"],\"timestamp\":\"2023-07-24T14:17:45.852Z\"},{\"traitId\":\"mQOIapcuoA3Cwyue\",\"values\":[\"2023-07-24\"],\"timestamp\":\"2023-07-24T14:17:45.852Z\"},{\"traitId\":\"jYJf-uOtE_TXc-Lp\",\"values\":[\"something\"],\"timestamp\":\"2023-07-24T14:17:45.852Z\"},{\"traitId\":\"DjDDgY-kKUksO90K\",\"values\":[\"3\"],\"timestamp\":\"2023-07-24T14:18:53.491Z\"},{\"traitId\":\"jItYsM6DUTHh_PQB\",\"values\":[\"3\",\"2\",\"1\"],\"timestamp\":\"2023-07-24T14:18:53.491Z\"}],\"0|1\":[{\"traitId\":\"YTcnavPoi2d3EaKF\",\"values\":[1],\"timestamp\":\"2023-07-24T14:17:56.163Z\"},{\"traitId\":\"DjDDgY-kKUksO90K\",\"values\":[\"2\"],\"timestamp\":\"2023-07-24T14:17:56.163Z\"},{\"traitId\":\"jItYsM6DUTHh_PQB\",\"values\":[\"4\",\"6\",\"2\"],\"timestamp\":\"2023-07-24T14:17:56.163Z\"},{\"traitId\":\"mQOIapcuoA3Cwyue\",\"values\":[\"2023-07-21\"],\"timestamp\":\"2023-07-24T14:17:56.163Z\"}],\"1|0\":[{\"traitId\":\"YTcnavPoi2d3EaKF\",\"values\":[1],\"timestamp\":\"2023-07-24T14:18:05.596Z\"},{\"traitId\":\"DjDDgY-kKUksO90K\",\"values\":[\"6\"],\"timestamp\":\"2023-07-24T14:18:05.596Z\"},{\"traitId\":\"jItYsM6DUTHh_PQB\",\"values\":[\"5\",\"3\",\"6\"],\"timestamp\":\"2023-07-24T14:18:05.596Z\"},{\"traitId\":\"mQOIapcuoA3Cwyue\",\"values\":[\"2023-07-26\"],\"timestamp\":\"2023-07-24T14:18:05.596Z\"},{\"traitId\":\"jYJf-uOtE_TXc-Lp\",\"values\":[\"2131\"],\"timestamp\":\"2023-07-24T14:18:05.596Z\"}],\"1|1\":[{\"traitId\":\"YTcnavPoi2d3EaKF\",\"values\":[1],\"timestamp\":\"2023-07-24T14:18:17.595Z\"},{\"traitId\":\"DjDDgY-kKUksO90K\",\"values\":[\"3\"],\"timestamp\":\"2023-07-24T14:18:17.595Z\"},{\"traitId\":\"jItYsM6DUTHh_PQB\",\"values\":[\"4\",\"3\",\"5\"],\"timestamp\":\"2023-07-24T14:18:17.595Z\"},{\"traitId\":\"mQOIapcuoA3Cwyue\",\"values\":[\"2023-07-22\"],\"timestamp\":\"2023-07-24T14:18:17.595Z\"},{\"traitId\":\"jYJf-uOtE_TXc-Lp\",\"values\":[\"rfsdfs\"],\"timestamp\":\"2023-07-24T14:18:17.595Z\"}],\"2|0\":[{\"traitId\":\"YTcnavPoi2d3EaKF\",\"values\":[0],\"timestamp\":\"2023-07-24T14:18:30.634Z\"},{\"traitId\":\"DjDDgY-kKUksO90K\",\"values\":[\"3\"],\"timestamp\":\"2023-07-24T14:18:30.634Z\"},{\"traitId\":\"jItYsM6DUTHh_PQB\",\"values\":[\"454\",\"3.55\",\"543.22\"],\"timestamp\":\"2023-07-24T14:18:30.634Z\"},{\"traitId\":\"mQOIapcuoA3Cwyue\",\"values\":[\"2023-07-23\"],\"timestamp\":\"2023-07-24T14:18:30.634Z\"},{\"traitId\":\"jYJf-uOtE_TXc-Lp\",\"values\":[\"fff\"],\"timestamp\":\"2023-07-24T14:18:30.634Z\"}],\"2|1\":[{\"traitId\":\"YTcnavPoi2d3EaKF\",\"values\":[0],\"timestamp\":\"2023-07-24T14:18:43.387Z\"},{\"traitId\":\"DjDDgY-kKUksO90K\",\"values\":[\"0\"],\"timestamp\":\"2023-07-24T14:18:43.387Z\"},{\"traitId\":\"jItYsM6DUTHh_PQB\",\"values\":[\"3\",\"5\",\"1\"],\"timestamp\":\"2023-07-24T14:18:43.387Z\"},{\"traitId\":\"mQOIapcuoA3Cwyue\",\"values\":[\"2023-07-25\"],\"timestamp\":\"2023-07-24T14:18:43.387Z\"},{\"traitId\":\"jYJf-uOtE_TXc-Lp\",\"values\":[\"33333\"],\"timestamp\":\"2023-07-24T14:18:43.387Z\"}]},\"trialTraitAddedTransactions\":[{\"name\":\"t3\",\"description\":null,\"dataType\":\"float\",\"allowRepeats\":true,\"setSize\":3,\"timeframe\":null,\"id\":\"jItYsM6DUTHh_PQB\"},{\"name\":\"t4\",\"description\":null,\"dataType\":\"date\",\"allowRepeats\":false,\"setSize\":1,\"timeframe\":null,\"id\":\"mQOIapcuoA3Cwyue\"},{\"name\":\"t5\",\"description\":null,\"dataType\":\"text\",\"allowRepeats\":false,\"setSize\":1,\"timeframe\":null,\"id\":\"jYJf-uOtE_TXc-Lp\"}]}", Transaction.class);

		ApiResult<Trial> result = sendTransaction(trial.getShareCodes().getOwnerCode(), t);
		Assertions.assertEquals(200, result.status);
		Assertions.assertEquals(5, result.data.getTraits().size());
		Assertions.assertEquals("t4", result.data.getTraits().get(3).getName());

		Cell cell = result.data.getData().get("0|0");
		List<Measurement> measurements = cell.getMeasurements().get(result.data.getTraits().get(1).getId());
		Assertions.assertEquals(4, measurements.size());
		Assertions.assertEquals("3", measurements.get(0).getValues().get(0));
		Assertions.assertEquals("4", measurements.get(1).getValues().get(0));
		Assertions.assertEquals("4", measurements.get(2).getValues().get(0));
		Assertions.assertEquals("3", measurements.get(3).getValues().get(0));
		measurements = cell.getMeasurements().get(result.data.getTraits().get(2).getId());
		Assertions.assertEquals(3, measurements.get(0).getValues().size());
		Assertions.assertEquals(3, measurements.get(1).getValues().size());
		Assertions.assertEquals("3", measurements.get(1).getValues().get(0));
		Assertions.assertEquals("2", measurements.get(1).getValues().get(1));
		Assertions.assertEquals("1", measurements.get(1).getValues().get(2));

		trial = result.data;
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
