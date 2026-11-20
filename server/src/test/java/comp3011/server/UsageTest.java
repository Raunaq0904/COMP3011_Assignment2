/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UsageTest {

    @Test
    void trackerIgnoresNonPositiveAmounts() {
        UsageTracker tracker = new UsageTracker();

        tracker.addSeconds(-5);
        tracker.addSeconds(0);

        assertEquals(0.0, tracker.totalSeconds());
    }

    @Test
    void trackerStaysExactWhenManyThreadsRecordUsageAtOnce() throws Exception {
        UsageTracker tracker = new UsageTracker();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            Thread thread = new Thread(() -> {
                for (int i = 0; i < 1000; i++) {
                    tracker.addSeconds(0.5);
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(4000.0, tracker.totalSeconds());
    }

    @Test
    void endpointReportsCumulativeUsageInSeconds() throws Exception {
        UsageTracker tracker = new UsageTracker();
        tracker.addSeconds(12.5);
        tracker.addSeconds(7.5);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new UsageController(tracker)).build();

        mvc.perform(get("/api/v1/global/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usage").value(20.0));
    }
}