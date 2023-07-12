package jhi.gridscore.server;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebListener;
import jhi.gridscore.server.util.ExpiredTrialExportTask;

import java.util.concurrent.*;

/**
 * The {@link ApplicationListener} is the main {@link ServletContextListener} of the application. It's started when the application is loaded by
 * Tomcat. It contains {@link #contextInitialized(ServletContextEvent)} which is executed on start and {@link #contextDestroyed(ServletContextEvent)}
 * which is executed when the application terminates.
 *
 * @author Sebastian Raubach
 */
@WebListener
public class ApplicationListener implements ServletContextListener
{
	private static ScheduledExecutorService backgroundScheduler;

	@Override
	public void contextInitialized(ServletContextEvent sce)
	{
		System.setProperty("org.jooq.no-logo", "true");
		System.setProperty("org.jooq.no-tips", "true");

		PropertyWatcher.initialize();

		backgroundScheduler = Executors.newSingleThreadScheduledExecutor();
		backgroundScheduler.scheduleAtFixedRate(new ExpiredTrialExportTask(), 0, 1, TimeUnit.DAYS);
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce)
	{
		PropertyWatcher.stopFileWatcher();

		try
		{
			// Stop the scheduler
			if (backgroundScheduler != null)
				backgroundScheduler.shutdownNow();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
