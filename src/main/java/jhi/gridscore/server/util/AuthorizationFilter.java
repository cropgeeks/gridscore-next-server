package jhi.gridscore.server.util;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.Provider;
import jhi.gridscore.server.PropertyWatcher;
import org.jooq.tools.StringUtils;

import java.io.IOException;
import java.lang.reflect.*;

/**
 * This filter makes sure that the {@link Secured} resources are only accessible by users with the correct user type.
 */
@Secured
@Provider
@Priority(Priorities.AUTHORIZATION)
public class AuthorizationFilter implements ContainerRequestFilter
{
	@Context
	private ResourceInfo resourceInfo;

	@Override
	public void filter(ContainerRequestContext requestContext)
			throws IOException
	{
		AuthenticationFilter.UserDetails userDetails = (AuthenticationFilter.UserDetails) requestContext.getSecurityContext().getUserPrincipal();

		String remoteToken = PropertyWatcher.get("secure.token");

		if (!StringUtils.isEmpty(remoteToken))
		{
			// Get the resource class which matches with the requested URL
			// Extract the roles declared by it
			Class<?> resourceClass = resourceInfo.getResourceClass();
			boolean classIsSecured = isSecured(resourceClass);

			// Get the resource method which matches with the requested URL
			// Extract the roles declared by it
			Method resourceMethod = resourceInfo.getResourceMethod();
			boolean methodIsSecured = isSecured(resourceMethod);

			if ((classIsSecured || methodIsSecured) && (userDetails == null || !userDetails.isValid()))
				requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build());
		}
	}

	// Extract the roles from the annotated element
	private boolean isSecured(AnnotatedElement annotatedElement)
	{
		if (annotatedElement == null)
		{
			return false;
		}
		else
		{
			Secured secured = annotatedElement.getAnnotation(Secured.class);
			return secured != null;
		}
	}
}
