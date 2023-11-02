package org.candlepin.integration.tests.resources.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.Notifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.jeasyarch.core.ManagedResource;
import io.jeasyarch.core.ServiceContext;
import io.jeasyarch.logging.FileServiceLoggingHandler;
import io.jeasyarch.logging.LoggingHandler;
import io.jeasyarch.utils.FileUtils;
import io.jeasyarch.utils.SocketUtils;
import org.apache.commons.lang3.NotImplementedException;

import java.io.File;

public class WiremockManagedResource extends ManagedResource {

    private static final String LOG_OUTPUT_FILE = "out.log";
    private static final String LOCALHOST = "localhost";

    private WireMockServer wireMockServer;
    private File logOutputFile;
    private LoggingHandler loggingHandler;

    @Override
    public String getDisplayName() {
        return context.getName();
    }

    @Override
    public void start() {
        loggingHandler = new FileServiceLoggingHandler(context.getOwner(), logOutputFile);
        loggingHandler.startWatching();

        wireMockServer.start();
    }

    @Override
    public void stop() {
        wireMockServer.stop();
    }

    @Override
    public String getHost() {
        return LOCALHOST;
    }

    @Override
    public int getFirstMappedPort() {
        return wireMockServer.port();
    }

    @Override
    public int getMappedPort(int port) {
        throw new NotImplementedException();
    }

    @Override
    public boolean isRunning() {
        return wireMockServer.isRunning();
    }

    public WireMockServer getWireMockServer() {
        return wireMockServer;
    }

    @Override
    protected LoggingHandler getLoggingHandler() {
        return loggingHandler;
    }

    @Override
    protected void init(ServiceContext context) {
        super.init(context);
        this.logOutputFile = new File(context.getServiceFolder().resolve(LOG_OUTPUT_FILE).toString());

        WireMockConfiguration config = new WireMockConfiguration();
        config.port(SocketUtils.findAvailablePort(context.getOwner()));
        config.notifier(new Notifier() {

            @Override
            public void info(String message) {
                FileUtils.copyContentTo(message, logOutputFile.toPath());
            }

            @Override
            public void error(String message) {
                FileUtils.copyContentTo(message, logOutputFile.toPath());
            }

            @Override
            public void error(String message, Throwable t) {
                FileUtils.copyContentTo(message, logOutputFile.toPath());
                FileUtils.copyContentTo(t.getMessage(), logOutputFile.toPath());
            }
        });
        wireMockServer = new WireMockServer(config);
    }
}
