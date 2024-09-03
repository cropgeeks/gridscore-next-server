package jhi.gridscore.server.resource;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jhi.gridscore.server.PropertyWatcher;
import jhi.gridscore.server.pojo.Settings;

@Path("settings")
public class SettingsResource
{
	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getSettings()
	{
		Settings result = new Settings();
		result.setPlausibleApiHost(PropertyWatcher.get("plausible.api.host"));
		result.setPlausibleDomain(PropertyWatcher.get("plausible.domain"));
		result.setPlausibleHashMode(PropertyWatcher.getBoolean("plausible.hash.mode"));

		return Response.ok(result).build();
	}

	@GET
	@Path("/version")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getVersion()
	{
		Package p = getClass().getPackage();

		String version = "DEVELOPMENT";
		if (p != null)
			version = p.getImplementationVersion();

		return Response.ok(version).build();
	}
}
