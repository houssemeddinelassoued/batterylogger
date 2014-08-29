package com.gmail.walles.johan.batterylogger;

import android.test.AndroidTestCase;
import com.androidplot.xy.XYSeries;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;

public class HistoryTest extends AndroidTestCase {
    private File testStorage;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        testStorage = File.createTempFile("historytest", ".txt");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        assertTrue(testStorage.delete());
    }

    private void assertValues(int index, double ... expectedValues) throws Exception {
        XYSeries batteryDrain = new History(testStorage).getBatteryDrain().get(index);
        double actualValues[] = new double[batteryDrain.size()];
        for (int i = 0; i < batteryDrain.size(); i++) {
            actualValues[i] = batteryDrain.getY(i).doubleValue();
        }
        assertEquals(Arrays.toString(expectedValues), Arrays.toString(actualValues));
    }

    private void assertDrainTimestamps(int index, Date ... expectedTimestamps) throws Exception {
        XYSeries series = new History(testStorage).getBatteryDrain().get(index);
        Date actualTimestamps[] = new Date[series.size()];
        for (int i = 0; i < series.size(); i++) {
            actualTimestamps[i] = History.toDate(series.getX(i));
        }
        assertEquals(Arrays.toString(expectedTimestamps), Arrays.toString(actualTimestamps));
    }

    private void assertEventTimestamps(Date ... expectedTimestamps) throws Exception {
        XYSeries series = new History(testStorage).getEvents();
        Date actualTimestamps[] = new Date[series.size()];
        for (int i = 0; i < series.size(); i++) {
            actualTimestamps[i] = History.toDate(series.getX(i));
        }
        assertEquals(Arrays.toString(expectedTimestamps), Arrays.toString(actualTimestamps));
    }

    private void assertEventDescriptions(String... expectedDescriptions) throws Exception {
        EventSeries events = new History(testStorage).getEvents();
        String actualDescriptions[] = new String[events.size()];
        for (int i = 0; i < events.size(); i++) {
            actualDescriptions[i] = events.getDescription(i);
        }
        assertEquals(Arrays.toString(expectedDescriptions), Arrays.toString(actualDescriptions));
    }

    private void assertNoEvents() throws Exception {
        assertEventTimestamps(/* Empty */);
        assertEventDescriptions(/* Empty */);
    }

    private void assertBatteryDrainSize(int expectedSize) throws Exception {
        assertEquals(expectedSize, new History(testStorage).getBatteryDrain().size());
    }

    public void testBlank() throws Exception {
        assertBatteryDrainSize(0);
        assertNoEvents();
    }

    public void testOnlyBatteryEvents() throws Exception {
        History testMe = new History(testStorage);
        testMe.addEvent(HistoryEvent.createBatteryLevelEvent(new Date(1 * History.HOUR_MS), 100));
        assertBatteryDrainSize(0);
        assertNoEvents();

        testMe.addEvent(HistoryEvent.createBatteryLevelEvent(new Date(3 * History.HOUR_MS), 98));
        assertBatteryDrainSize(1);
        assertNoEvents();

        // Drain timestamp should be between the sample timestamps
        assertDrainTimestamps(0, new Date(2 * History.HOUR_MS));
        // From 100% to 98% in two hours = 1%/h
        assertValues(0, 1.0);

        testMe.addEvent(HistoryEvent.createBatteryLevelEvent(new Date(5 * History.HOUR_MS), 94));
        assertBatteryDrainSize(1);
        assertNoEvents();

        assertDrainTimestamps(0, new Date(2 * History.HOUR_MS), new Date(4 * History.HOUR_MS));
        // From 100% to 98% in two hours = 1%/h
        // From 98% to 94% in two hours = 2%/h
        assertValues(0, 1.0, 2.0);
    }

    public void testRebootEvents() throws Exception {
        History testMe = new History(testStorage);
        testMe.addEvent(HistoryEvent.createBatteryLevelEvent(new Date(1 * History.HOUR_MS), 51));
        testMe.addEvent(HistoryEvent.createBatteryLevelEvent(new Date(3 * History.HOUR_MS), 50));

        testMe.addEvent(HistoryEvent.createSystemHaltingEvent(new Date(5 * History.HOUR_MS)));
        testMe.addEvent(HistoryEvent.createBatteryLevelEvent(new Date(7 * History.HOUR_MS), 51));
        testMe.addEvent(HistoryEvent.createSystemBootingEvent(new Date(9 * History.HOUR_MS), true));

        testMe.addEvent(HistoryEvent.createBatteryLevelEvent(new Date(11 * History.HOUR_MS), 50));
        testMe.addEvent(HistoryEvent.createBatteryLevelEvent(new Date(13 * History.HOUR_MS), 47));

        assertBatteryDrainSize(2);
        assertValues(0, 0.5);
        assertDrainTimestamps(0, new Date(2 * History.HOUR_MS));
        assertValues(1, 1.5);
        assertDrainTimestamps(1, new Date(12 * History.HOUR_MS));

        assertEventTimestamps(new Date(5 * History.HOUR_MS), new Date(9 * History.HOUR_MS));
        assertEventDescriptions("System shutting down", "System starting up (charging)");
    }

    /**
     * This would happen at unclean shutdowns; device crashes, battery runs out or is removed.
     */
    public void testMissingShutdownEvent() throws Exception {
        History testMe = new History(testStorage);
        testMe.addEvent(HistoryEvent.createBatteryLevelEvent(new Date(1 * History.HOUR_MS), 51));
        testMe.addEvent(HistoryEvent.createBatteryLevelEvent(new Date(3 * History.HOUR_MS), 50));

        testMe.addEvent(HistoryEvent.createBatteryLevelEvent(new Date(7 * History.HOUR_MS), 48));
        testMe.addEvent(HistoryEvent.createSystemBootingEvent(new Date(9 * History.HOUR_MS), false));

        testMe.addEvent(HistoryEvent.createBatteryLevelEvent(new Date(11 * History.HOUR_MS), 46));
        testMe.addEvent(HistoryEvent.createBatteryLevelEvent(new Date(13 * History.HOUR_MS), 45));

        // Assume unclean shutdown between last known event before the boot event and the boot event
        assertBatteryDrainSize(2);
        assertValues(0, 0.5, 0.5);
        assertDrainTimestamps(0, new Date(2 * History.HOUR_MS), new Date(5 * History.HOUR_MS));
        assertValues(1, 0.5);
        assertDrainTimestamps(1, new Date(12 * History.HOUR_MS));

        assertEventDescriptions("Unclean shutdown", "System starting up (not charging)");
        assertEventTimestamps(new Date(8 * History.HOUR_MS), new Date(9 * History.HOUR_MS));
    }

    public void testMedian() {
        assertEquals(4.0, History.median(Arrays.asList(4.0)));
        assertEquals(4.5, History.median(Arrays.asList(4.0, 5.0)));
        assertEquals(5.0, History.median(Arrays.asList(4.0, 5.0, 6.0)));
        assertEquals(5.5, History.median(Arrays.asList(4.0, 5.0, 6.0, 7.0)));
    }

    public void testMaintainFileSize() throws Exception {
        History testMe = new History(testStorage);
        Date now = new Date();
        long lastFileSize = 0;

        char[] array = new char[10 * 1024];
        Arrays.fill(array, 'x');
        final String longEventDescription = new String(array);

        while (true) {
            testMe.addEvent(HistoryEvent.createInfoEvent(now, longEventDescription));

            long fileSize = testStorage.length();
            assertTrue("File should have been truncated before 500kb: " + fileSize,
                    fileSize < 500 * 1024);

            if (fileSize < lastFileSize) {
                // It got truncated
                assertTrue("File should have been allowed to grow to at least 350kb: " + lastFileSize,
                        lastFileSize > 350 * 1024);
                assertTrue("File should have been truncated to 250kb-350kb: " + fileSize,
                        fileSize > 250 * 1024 && fileSize < 350 * 1024);
                return;
            }

            lastFileSize = fileSize;
        }
    }

    private void assertHistory(String expected, History history) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (XYSeries xySeries : history.getDrainLines()) {
            assertEquals(2, xySeries.size());
            assertEquals(xySeries.getY(0), xySeries.getY(1));
            double y = xySeries.getY(0).doubleValue();
            assertTrue(y >= 0);

            char type;
            if (y == 0) {
                type = '_';
            } else {
                type = '-';
            }

            double lengthHours =
                    (xySeries.getX(1).doubleValue() - xySeries.getX(0).doubleValue()) / 3600.0;
            // Round lengthHours so that half an hour is rounded up to 1
            int numberOfChars = (int)(lengthHours + 0.6);
            assertTrue(numberOfChars > 0);
            for (int i = 0; i < numberOfChars; i++) {
                builder.append(type);
            }
        }

        // "Don't know" should render as "empty" just like downtime, where we don't know either
        expected = expected.replace('?', ' ');
        assertEquals(expected, builder.toString());
    }

    private static char firstNonQuestionmarkChar(final String pattern) {
        char firstNonQuestionmarkChar = '\0';
        for (int i = 0; i < pattern.length(); i++) {
            char currentChar = pattern.charAt(i);
            if (currentChar != '?') {
                firstNonQuestionmarkChar = currentChar;
                break;
            }
        }
        if (firstNonQuestionmarkChar == '\0') {
            throw new IllegalArgumentException("Pattern contains no non-questionmark chars: <" + pattern + ">");
        }

        return firstNonQuestionmarkChar;
    }

    private SystemState createInitialState(final String pattern, final Date t0)
            throws IOException
    {
        char firstNonQuestionmarkChar = firstNonQuestionmarkChar(pattern);
        Date bootTimestamp = new Date(t0.getTime() - 86400 * 1000);
        if (firstNonQuestionmarkChar == '-') {
            return new SystemState(t0, 50, false, bootTimestamp);
        } else if (firstNonQuestionmarkChar == '_') {
            return new SystemState(t0, 50, true, bootTimestamp);
        } else {
            throw new IllegalArgumentException("First non-? char must be either _ or -: " + pattern);
        }
    }

    private History createInitialHistory(final String pattern, final Date t0) throws IOException {
        History history = History.createEmptyHistory();
        switch (pattern.charAt(0)) {
            case '-':
                history.addEvent(HistoryEvent.createStopChargingEvent(t0));
                break;
            case '_':
                history.addEvent(HistoryEvent.createStartChargingEvent(t0));
                break;
        }
        history.addEvent(HistoryEvent.createBatteryLevelEvent(t0, 50));

        return history;
    }

    private static SystemState createNextState(SystemState previousState, char controlChar) {
        Date nextTimestamp = new Date(previousState.getTimestamp().getTime() + 3600 * 1000);

        int nextPercentage = previousState.getBatteryPercentage();
        boolean nextCharging;
        if (controlChar == '-') {
            nextCharging = false;
            nextPercentage -= 1;
        } else if (controlChar == '_') {
            nextCharging = true;
            nextPercentage += 20;
            if (nextPercentage > 100) {
                nextPercentage = 100;
            }
        } else {
            throw new UnsupportedOperationException("Unsupported control char " + controlChar);
        }

        Date nextBootTimestamp = previousState.getBootTimestamp();

        return new SystemState(nextTimestamp, nextPercentage, nextCharging, nextBootTimestamp);
    }

    private void testDrainLines(String pattern) throws IOException {
        // Create a history with one one or two initial events matching the first pattern character
        Date t0 = new Date();
        SystemState previousState = createInitialState(pattern, t0);
        History history = createInitialHistory(pattern, t0);

        for (int i = 0; i < pattern.length(); i++) {
            char controlChar = pattern.charAt(i);
            SystemState currentState = createNextState(previousState, controlChar);
            for (HistoryEvent event : currentState.getEventsSince(previousState)) {
                history.addEvent(event);
            }
            previousState = currentState;
        }

        assertHistory(pattern, history);
    }

    public void testDrainLines() throws IOException {
        testDrainLines("-");
        testDrainLines("--");
        testDrainLines("---");
        testDrainLines("----");
        testDrainLines("?-");

        testDrainLines("_");
        testDrainLines("__");
        testDrainLines("___");
        testDrainLines("____");
        testDrainLines("?_");

        testDrainLines(" ");
        testDrainLines("  ");
        testDrainLines("   ");
        testDrainLines("    ");
        testDrainLines("? ");

        testDrainLines("-_");
        testDrainLines("_-");
        testDrainLines("- ");
        testDrainLines(" -");
        testDrainLines("_ ");
        testDrainLines(" _");

        testDrainLines("--__--");
        testDrainLines("__--__");
        testDrainLines("  __  ");
        testDrainLines("  --  ");
        testDrainLines("__  __");
        testDrainLines("--  --");
    }
}
