/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 */
package comp3011.server;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/global")
public class UsageController {

    private final UsageTracker usageTracker;

    public UsageController(UsageTracker usageTracker) {
        this.usageTracker = usageTracker;
    }

    @GetMapping("/usage")
    public UsageResponse usage() {
        return new UsageResponse(usageTracker.totalSeconds());
    }
}