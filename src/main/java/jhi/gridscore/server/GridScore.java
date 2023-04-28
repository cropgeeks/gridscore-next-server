package jhi.gridscore.server;

import jakarta.ws.rs.ApplicationPath;
import org.glassfish.jersey.server.ResourceConfig;

@ApplicationPath("/api/")
public class GridScore extends ResourceConfig
{
	public GridScore()
	{
		packages("jhi.gridscore.server");
	}
}
