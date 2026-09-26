package com.somil.jobportal.ai.embedding;

import java.time.Duration;

/** Waits between retries. Tests swap it for one that records delays instead of sleeping. */
@FunctionalInterface
public interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;

    Sleeper REAL = duration -> Thread.sleep(duration.toMillis());
}
