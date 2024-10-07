import com.google.gson.Gson;
import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.*;
import jhi.gridscore.server.pojo.*;
import jhi.gridscore.server.pojo.transaction.TrialContent;
import org.junit.jupiter.api.*;

import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RemoteShareTest extends ConfigTest
{
	private static Trial                    localTrial;
	private static Trial                    remoteTrial;
	private static Gson                     gson         = new Gson();
	private static Map<String, List<Trial>> sharedTrials = new HashMap<>();

	private static final String REMOTE_URL = "https://gridscore.hutton.ac.uk/api/trial";

	/**
	 * Create the initial configuration.
	 */
	@BeforeAll
	static void setUp()
			throws Exception
	{
		try (InputStreamReader is = new InputStreamReader(GerminateExportTest.class.getResourceAsStream("barley.json")))
		{
			localTrial = gson.fromJson(is, Trial.class);
		}
		try (InputStreamReader is = new InputStreamReader(GerminateExportTest.class.getResourceAsStream("barley.json")))
		{
			remoteTrial = gson.fromJson(is, Trial.class);
		}

		setUpClient(null);
	}

	@Order(1)
	@Test
	void shareLocal()
	{
		ApiResult<Trial> result = sendTrial(null, localTrial);
		Assertions.assertEquals(200, result.status);
		localTrial = result.data;
		Assertions.assertNotNull(localTrial.getShareCodes().getOwnerCode());
		sharedTrials.put(URL, List.of(localTrial));
	}

	@Order(2)
	@Test
	void shareRemote()
	{

		setUpClient(REMOTE_URL);
		ApiResult<Trial> result = sendTrial(REMOTE_URL, remoteTrial);
		Assertions.assertEquals(200, result.status);
		remoteTrial = result.data;
		Assertions.assertNotNull(remoteTrial.getShareCodes().getOwnerCode());
		sharedTrials.put(REMOTE_URL, List.of(remoteTrial));
	}

	@Order(3)
	@Test
	void updateTrials()
	{
		Transaction localTransaction = new Transaction();
		Transaction remoteTransaction = new Transaction();

		localTrial.setName("Local trial");
		remoteTrial.setName("Remote trial");
		localTransaction.setTrialEditTransaction(new TrialContent().setName(localTrial.getName()));
		ApiResult<Trial> result = sendTransaction(null, localTrial.getShareCodes().getOwnerCode(), localTransaction);
		localTrial = result.data;
		Assertions.assertEquals(200, result.status);
		Assertions.assertEquals("Local trial", localTrial.getName());

		remoteTransaction.setTrialEditTransaction(new TrialContent().setName(remoteTrial.getName()));
		result = sendTransaction(REMOTE_URL, remoteTrial.getShareCodes().getOwnerCode(), remoteTransaction);
		remoteTrial = result.data;
		Assertions.assertEquals(200, result.status);
		Assertions.assertEquals("Remote trial", remoteTrial.getName());
	}

	@Order(4)
	@Test
	void checkStatus()
	{
		sharedTrials.forEach((url, trial) -> {
			WebTarget target = client.target(url + "/checkupdate");
			Response response = target.request(MediaType.APPLICATION_JSON)
									  .post(Entity.json(trial.stream().map(t -> t.getShareCodes().getOwnerCode()).collect(Collectors.toList())));
			Assertions.assertEquals(response.getStatus(), 200);
			Map<String, TrialTimestamp> result = response.readEntity(new GenericType<>()
			{
			});
			Assertions.assertEquals(result.size(), trial.size());
			result.forEach((id, ts) -> {
				Assertions.assertNotEquals(id.length(), 0);
				Assertions.assertNotNull(ts);
			});
		});
	}

	/**
	 * Remove the configuration from the database again.
	 */
	@AfterAll
	static void breakDown()
	{
		sharedTrials.forEach((url, codes) -> {
			codes.forEach(t -> {
				WebTarget target = client.target(url)
										 .path(t.getShareCodes().getOwnerCode())
										 .queryParam("name", t.getName());

				Response resp = target.request(MediaType.APPLICATION_JSON)
									  .delete();

				Assertions.assertEquals(200, resp.getStatus());
			});
		});
	}
}
