package io.datapulse.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class HeartbeatJob extends QuartzJobBean {
    private static final Logger log = LoggerFactory.getLogger(HeartbeatJob.class);

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("datapulse heartbeat");
    }
}