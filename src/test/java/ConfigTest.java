import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.*;
import jhi.gridscore.server.pojo.*;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.junit.platform.commons.util.StringUtils;

import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Date;

public abstract class ConfigTest
{
	protected static String             URL = "http://localhost:8180/gridscore-next-api/v2.0.2/api/trial";
	protected static Client             client;
	protected static Invocation.Builder postBuilder;

	protected static String formatDate(Date date) {
		ZonedDateTime time = ZonedDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
		return time.format(new DateTimeFormatterBuilder().appendInstant(3).toFormatter());
	}

	protected static void setUpClient()
	{
		client = ClientBuilder.newBuilder()
							  .build();

		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXX"));
		// Create a Jackson Provider
		JacksonJaxbJsonProvider jsonProvider = new JacksonJaxbJsonProvider(objectMapper, JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS);
		client.register(jsonProvider);

		postBuilder = client.target(URL)
							.request(MediaType.APPLICATION_JSON);
	}

	protected ApiResult<Trial> getTrial(String shareCode)
	{
		WebTarget target = client.target(URL);

		if (!StringUtils.isBlank(shareCode))
			target = target.path(shareCode);

		Response response = target.request(MediaType.APPLICATION_JSON)
								  .get();

		int code = response.getStatus();
		Trial result = code == 200 ? response.readEntity(Trial.class) : null;

		return new ApiResult<Trial>().setData(result).setStatus(code);
	}

	protected ApiResult<Trial> sendTrial(Trial config)
	{
		Response response = client.target(URL)
								  .path("share")
								  .request(MediaType.APPLICATION_JSON)
								  .post(Entity.entity(config, MediaType.APPLICATION_JSON));

		int code = response.getStatus();
		Trial result = code == 200 ? response.readEntity(Trial.class) : null;

		return new ApiResult<Trial>().setData(result).setStatus(code);
	}

	protected ApiResult<Trial> sendTransaction(String shareCode, Transaction transaction) {
		Response response = client.target(URL)
								  .path(shareCode + "/transaction")
								  .request(MediaType.APPLICATION_JSON)
								  .post(Entity.entity(transaction, MediaType.APPLICATION_JSON));

		int code = response.getStatus();
		Trial result = code == 200 ? response.readEntity(Trial.class) : null;

		return new ApiResult<Trial>().setData(result).setStatus(code);
	}

	protected static class ApiResult<T>
	{
		public T   data;
		public int status;

		public ApiResult<T> setData(T data)
		{
			this.data = data;
			return this;
		}

		public ApiResult<T> setStatus(int status)
		{
			this.status = status;
			return this;
		}
	}
}
