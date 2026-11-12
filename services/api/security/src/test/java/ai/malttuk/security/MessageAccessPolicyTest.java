package ai.malttuk.security;

import ai.malttuk.security.MessageAccessPolicy.Decision;

/** Standalone regression checks. Throws AssertionError without requiring -ea. */
public final class MessageAccessPolicyTest {
    private MessageAccessPolicyTest() {
    }

    public static void main(String[] args) {
        expect("designated recipient", "user-a", "user-a", Decision.ALLOW);
        expect("different recipient", "user-b", "user-a", Decision.DENY);
        expect("missing authentication", null, "user-a", Decision.REQUIRE_AUTHENTICATION);
        expect("empty authentication", "", "user-a", Decision.REQUIRE_AUTHENTICATION);
        expect("blank authentication", " \t\n", "user-a", Decision.REQUIRE_AUTHENTICATION);
        expect("missing message", "user-a", null, Decision.DENY);
        expect("empty stored recipient", "user-a", "", Decision.DENY);
        expect("blank stored recipient", "user-a", " \t", Decision.DENY);
        expect("both IDs absent", null, null, Decision.REQUIRE_AUTHENTICATION);
        expect("both IDs blank", "", "", Decision.REQUIRE_AUTHENTICATION);
        expect("case is significant", "USER-A", "user-a", Decision.DENY);
        expect("prefix is not identity", "user", "user-a", Decision.DENY);
        expect("leading space is significant", " user-a", "user-a", Decision.DENY);
        expect("trailing space is significant", "user-a ", "user-a", Decision.DENY);
        expect("lookalike Unicode is distinct", "user-\u0430", "user-a", Decision.DENY);
        expect("opaque Unicode ID", "\uC218\uC2E0\uC790-a", "\uC218\uC2E0\uC790-a", Decision.ALLOW);
        System.out.println("All 16 message access policy checks passed.");
    }

    private static void expect(String scenario, String userId, String recipientId,
                               Decision expected) {
        Decision actual = MessageAccessPolicy.evaluate(userId, recipientId);
        if (actual != expected) {
            throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
        }
        System.out.println("[PASS] " + scenario);
    }
}
