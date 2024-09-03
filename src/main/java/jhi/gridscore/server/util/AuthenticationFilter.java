package jhi.gridscore.server.util;

import jakarta.annotation.Priority;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.*;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.Provider;
import jhi.gridscore.server.PropertyWatcher;
import org.jooq.tools.StringUtils;

import java.io.IOException;
import java.security.Principal;
import java.util.Objects;

/**
 * Filter that checks if restricted resources are only accessed if a valid Bearer token is set.
 */
@Secured
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationFilter implements ContainerRequestFilter
{
	private static final String REALM                 = "gridscore";
	private static final String AUTHENTICATION_SCHEME = "Bearer";

	@Context
	private HttpServletRequest  request;
	@Context
	private HttpServletResponse response;
	@Context
	ServletContext servletContext;
	@Context
	private ResourceInfo resourceInfo;

	@Override
	public void filter(ContainerRequestContext requestContext)
			throws IOException
	{
		// Get the Authorization header from the request
		String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

		String remoteToken = PropertyWatcher.get("secure.token");
		if (StringUtils.isEmpty(remoteToken))
		{
			return;
		}

		// Extract the token from the Authorization header
		String token;

		if (!StringUtils.isEmpty(authorizationHeader))
			token = authorizationHeader.substring(AUTHENTICATION_SCHEME.length()).trim();
		else
			token = null;

		if (Objects.equals(token, "null"))
			token = null;

		final SecurityContext currentSecurityContext = requestContext.getSecurityContext();
		requestContext.setSecurityContext(new SecurityContext()
		{
			@Override
			public Principal getUserPrincipal()
			{
				return new UserDetails(true);
			}

			@Override
			public boolean isUserInRole(String role)
			{
				return true;
			}

			@Override
			public boolean isSecure()
			{
				return currentSecurityContext.isSecure();
			}

			@Override
			public String getAuthenticationScheme()
			{
				return AUTHENTICATION_SCHEME;
			}
		});

		try
		{
			validateToken(token);
		}
		catch (Exception e)
		{
			abortWithUnauthorized(requestContext);
		}
	}

	private void validateToken(String token)
	{
		String secureToken = PropertyWatcher.get("secure.token");

		if (!StringUtils.isEmpty(secureToken) && !Objects.equals(token, secureToken))
			throw new RuntimeException();
	}

	private void abortWithUnauthorized(ContainerRequestContext requestContext)
	{

		// Abort the filter chain with a 401 status code response
		// The WWW-Authenticate header is sent along with the response
		requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
										 .header(HttpHeaders.WWW_AUTHENTICATE, AUTHENTICATION_SCHEME + " realm=\"" + REALM + "\"")
										 .build());
	}

	public static class UserDetails implements Principal
	{
		private boolean valid = false;

		public UserDetails(boolean valid)
		{
			this.valid = valid;
		}

		public boolean isValid()
		{
			return valid;
		}

		@Override
		public String getName()
		{
			return Boolean.toString(valid);
		}
	}
}
