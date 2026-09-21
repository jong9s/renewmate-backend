package com.renewmate.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

class SlackAlertPipelineTest {

    @Test
    void simulatedFailureTriggersCiAlertPipeline() {
        fail("SIMULATED_CI_FAILURE: Slack alert pipeline verification only; never merge this test");
    }
}
