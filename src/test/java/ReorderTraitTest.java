import com.google.gson.Gson;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.*;
import jhi.gridscore.server.pojo.*;
import org.junit.jupiter.api.*;

import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Stream;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ReorderTraitTest extends ConfigTest
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
		ApiResult<Trial> result = sendTrial(null, trial);
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
		List<String> reorderedIds = trial.getTraits().reversed().stream().map(Trait::getId).toList();
		t.setTraitOrderTransaction(reorderedIds);

		ApiResult<Trial> result = sendTransaction(null, trial.getShareCodes().getOwnerCode(), t);
		Assertions.assertEquals(200, result.status);

		Assertions.assertIterableEquals(reorderedIds, result.data.getTraits().stream().map(Trait::getId).toList());
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
