package jhi.gridscore.server.util;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

@WebFilter(urlPatterns = "/*") // Intercepts all incoming API and web requests
public class CatchAllExceptionFilter implements Filter
{
	private final PlausibleExceptionHandler exceptionTracker = new PlausibleExceptionHandler();

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException
	{
		try
		{
			// Let the request pass normally down the execution chain
			chain.doFilter(request, response);
		}
		catch (Throwable throwable)
		{
			/// 1. Cast to HTTP request to get access to web details
			if (request instanceof HttpServletRequest httpRequest)
			{
				StringBuffer requestURL = httpRequest.getRequestURL();
				String queryString = httpRequest.getQueryString();

				// Reconstruct full URL (e.g., https://api.myproject.com/v1/users?id=5)
				String fullUrl = (queryString == null)
						? requestURL.toString()
						: requestURL.append('?').append(queryString).toString();

				// 2. Pass it to your tracker
				exceptionTracker.trackException(throwable, fullUrl);
			}
			else
			{
				exceptionTracker.trackException(throwable, "Non-HTTP Request");
			}

			// Bubble up or handle gracefully so the client gets a clean response
			throw throwable;
		}
	}

	@Override
	public void init(FilterConfig filterConfig)
	{
	}

	@Override
	public void destroy()
	{
	}
}