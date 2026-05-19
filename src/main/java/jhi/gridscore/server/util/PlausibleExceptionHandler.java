package jhi.gridscore.server.util;

import jhi.gridscore.server.PropertyWatcher;
import org.jooq.tools.StringUtils;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.logging.Logger;

public class PlausibleExceptionHandler
{
	private static final String APP_PACKAGE_PREFIX = "jhi.gridscore";
	private static       String PLAUSIBLE_API_URL;
	private static       String PLAUSIBLE_DOMAIN;

	private final HttpClient httpClient;

	private final boolean active;

	public PlausibleExceptionHandler()
	{
		this.httpClient = HttpClient.newBuilder()
		                            .connectTimeout(Duration.ofSeconds(3))
		                            .build();

		String domain = PropertyWatcher.get("plausible.server.exception.domain");
		String host = PropertyWatcher.get("plausible.server.exception.host");

		if (!StringUtils.isBlank(domain) && !StringUtils.isBlank(host))
		{
			active = true;
			PLAUSIBLE_API_URL = host + "/api/event";
			PLAUSIBLE_DOMAIN = domain;
		}
		else
		{
			active = false;
			PLAUSIBLE_API_URL = null;
			PLAUSIBLE_DOMAIN = null;
		}
	}

	public void trackException(Throwable throwable, String serviceName)
	{
		// NOOP
		if (!active)
			return;

		String exceptionClass = throwable.getClass().getName();
		String message = throwable.getMessage() != null ? throwable.getMessage() : "No message";

		// Truncate message to avoid exceeding Plausible's payload limits if necessary
		if (message.length() > 200)
		{
			message = message.substring(0, 197) + "...";
		}

		// Extract file and line number safely
		String fileName = "Unknown";
		int lineNumber = -1;

		StackTraceElement[] trace = throwable.getStackTrace();
		if (trace != null && trace.length > 0)
		{
			StackTraceElement targetFrame = null;

			// Scan the stack trace for the first frame inside your package
			for (StackTraceElement frame : trace)
			{
				String className = frame.getClassName();
				if (className.startsWith(APP_PACKAGE_PREFIX))
				{
					// CRITICAL SKIP: Ignore your own tracking infrastructure classes
					if (className.equals(PlausibleExceptionHandler.class.getName()) ||
							className.endsWith("CatchAllExceptionFilter") ||
							className.endsWith("GlobalExceptionHandler"))
					{
						continue; // Skip this frame and keep looking down the stack
					}

					targetFrame = frame;
					break; // Stop at the very first (most recent) match in your code
				}
			}

			// Fallback: If your code isn't in the stack trace, use the top-most frame
			if (targetFrame == null)
				targetFrame = trace[0];

			fileName = targetFrame.getFileName() != null ? targetFrame.getFileName() : "Unknown";
			lineNumber = targetFrame.getLineNumber();
		}

		String location = fileName + ":" + lineNumber;

		// Construct raw JSON payload (or use Jackson/Gson)
		String jsonPayload = String.format("""
						{
						  "name": "Exception Thrown",
						  "url": "http://backend/errors/%s",
						  "domain": "%s",
						  "props": {
						    "class": "%s",
						    "message": "%s",
						    "service": "%s",
						    "location": "%s"
						  }
						}
						""",
				throwable.getClass().getSimpleName(),
				PLAUSIBLE_DOMAIN,
				exceptionClass,
				message.replace("\"", "\\\""), // Basic escaping for safety
				serviceName,
				location
		);

		HttpRequest request = HttpRequest.newBuilder()
		                                 .uri(URI.create(PLAUSIBLE_API_URL))
		                                 .header("Content-Type", "application/json")
		                                 // CRITICAL: Set fake/real client headers so Plausible doesn't flag it as a bot
		                                 .header("User-Agent", "Mozilla/5.0 (Java Backend Exception Tracker)")
		                                 .header("X-Forwarded-For", "127.0.0.1")
		                                 .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
		                                 .timeout(Duration.ofSeconds(2))
		                                 .build();

		// Send asynchronously so you don't block your application threads on error handling
		httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
		          .thenAccept(response -> {
					  if (response.statusCode() != 202)
					  {
						  Logger.getLogger("").info("Failed to send exception to Plausible. Status: " + response.statusCode());
					  }
					  // Debugging hint: Check if Plausible dropped it via bot filter
					  if (response.headers().firstValue("x-plausible-dropped").isPresent())
					  {
						  Logger.getLogger("").info("Plausible silently dropped the event via bot filtering.");
					  }
				  })
		          .exceptionally(ex -> {
					  Logger.getLogger("").info("Error communicating with Plausible: " + ex.getMessage());
					  return null;
				  }).join();
	}
}
