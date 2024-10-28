package jhi.gridscore.server;

import jakarta.ws.rs.ApplicationPath;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

@ApplicationPath("/api/")
public class GridScore extends ResourceConfig
{
	public GridScore()
	{
		packages("jhi.gridscore.server");

		register(MultiPartFeature.class);
	}
}
