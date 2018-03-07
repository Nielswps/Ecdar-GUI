package ecdar.mutation;
import ecdar.Ecdar;
import ecdar.abstractions.EdgeStatus;
import ecdar.mutation.models.*;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A test driver that runs testcases on a system under test (sut).
 */
public class TestDriver implements ConcurrentJobsHandler {
    private List<String> passed;
    private List<String> failed;
    private List<String> inconclusive;
    private final MutationTestPlan testPlan;
    private final long timeUnit;
    private final int bound;
    private final List<MutationTestCase> mutationTestCases;
    private final Consumer<Text> progressWriterText;
    private Instant generationStart;
    private ConcurrentJobsDriver jobsDriver;

    private enum Verdict {NONE, INCONCLUSIVE, PASS, FAIL}

    TestDriver(final List<MutationTestCase> mutationTestCases, final MutationTestPlan testPlan, final Consumer<Text> progressWriterText, final long timeUnit, final int bound) {
        this.mutationTestCases = mutationTestCases;
        this.progressWriterText = progressWriterText;
        this.testPlan = testPlan;
        this.timeUnit = timeUnit;
        this.bound = bound;
    }

    /**
     * Starts the test driver.
     */
    public void start() {
        generationStart = Instant.now();
        inconclusive = new ArrayList<>();
        passed = new ArrayList<>();
        failed = new ArrayList<>();
        jobsDriver = new ConcurrentJobsDriver(this, mutationTestCases.size());
        jobsDriver.start();
    }

    /**
     * Performs a testcase on the testplans system under test(sut).
     * @param testCase to perform.
     */
    private void performTest(final MutationTestCase testCase) {
        new Thread(() -> {
            final Process sut;
            final BufferedWriter output;
            final InputStream inputStream;
            final BufferedReader input;
            ObjectProperty<Instant> lastUpdateTime = new SimpleObjectProperty<>();
            lastUpdateTime.setValue(Instant.now());
            NonRefinementStrategy strategy = testCase.getStrategy();
            SimpleComponentSimulation testModelSimulation = new SimpleComponentSimulation(testCase.getTestModel());
            SimpleComponentSimulation mutantSimulation = new SimpleComponentSimulation(testCase.getMutant());

            try {
                sut = Runtime.getRuntime().exec("java -jar " + Ecdar.projectDirectory.get() + File.separator + getPlan().getSutPath().replace("/", File.separator));
                output = new BufferedWriter(new OutputStreamWriter(sut.getOutputStream()));
                inputStream = sut.getInputStream();
                input = new BufferedReader(new InputStreamReader(inputStream));
            } catch (final IOException e) {
                e.printStackTrace();
                return;
            }

            for(int step = 0; step < bound; step++) {
                // Get rule and check if its empty
                StrategyRule rule = strategy.getRule(testModelSimulation, mutantSimulation);
                if (rule == null) {
                    inconclusive.add(testCase.getId());
                    onTestDone();
                    return;
                }

                //Check if rule is an action rule, if it is an output action perform delay,
                //If it is an input perform it
                if (rule instanceof ActionRule) {
                    if (((ActionRule) rule).getStatus() == EdgeStatus.OUTPUT) {
                        Verdict verdict = delayForOutput(testModelSimulation, mutantSimulation, testCase, lastUpdateTime, inputStream, input);
                        if(!verdict.equals(Verdict.NONE)) {
                            onTestDone();
                            return;
                        }
                    } else {
                        try {
                            testModelSimulation.runInputAction(((ActionRule) rule).getSync());
                            mutantSimulation.runInputAction(((ActionRule) rule).getSync());
                        } catch (MutationTestingException e) {
                            e.printStackTrace();
                        }
                        String sync = ((ActionRule) rule).getSync();
                        writeToSut(sync, output, sut);
                    }
                } else if (rule instanceof DelayRule) {
                    Verdict verdict = delay(testModelSimulation, mutantSimulation, testCase, lastUpdateTime, inputStream, input);
                    if(!verdict.equals(Verdict.NONE)) {
                        onTestDone();
                        return;
                    }
                }
            }
            inconclusive.add(testCase.getId());
            onTestDone();
        }).start();
    }

    /**
     * Is triggered when a test-case execution is done.
     * It updates UI labels to tell user about the progress.
     * It also updates the jobsDriver about the job progress.
     */
    private synchronized void onTestDone() {
        Platform.runLater(() -> getPlan().setTestCasesText("Test-cases: " + mutationTestCases.size() + " - Execution time: " +
                MutationTestPlanPresentation.readableFormat(Duration.between(generationStart, Instant.now()))));
        Platform.runLater(() -> getPlan().setInconclusiveText("Inconclusive: " + inconclusive.size()));
        Platform.runLater(() -> getPlan().setPassedText("Passed: " + passed.size()));
        Platform.runLater(() -> getPlan().setFailedText("Failed: " + failed.size()));

        jobsDriver.onJobDone();
    }

    /**
     * performs a delay on the simulations and itself and checks if the system under test made an output during the delay.
     * @param testModelSimulation simulation representing the test model.
     * @param mutantSimulation simulation representing the mutated model.
     * @param testCase that is being performed.
     * @return a verdict, it is NONE if no verdict were reached from this delay.
     */
    private Verdict delay(final SimpleComponentSimulation testModelSimulation, final SimpleComponentSimulation mutantSimulation, final MutationTestCase testCase, ObjectProperty<Instant> lastUpdateTime, final InputStream inputStream, final BufferedReader input){
        try {
            //Check if any output is ready, if there is none, do delay
            if (inputStream.available() == 0) {
                Thread.sleep(timeUnit);
                //Do Delay
                final double waitedTimeUnits = Duration.between(lastUpdateTime.get(), Instant.now()).toMillis()/(double)timeUnit;
                lastUpdateTime.setValue(Instant.now());
                if (!testModelSimulation.delay(waitedTimeUnits)) {
                    failed.add(testCase.getId());
                    return Verdict.FAIL;
                } else {
                    if (!mutantSimulation.delay(waitedTimeUnits)) {
                        //Todo Handle exception
                    }
                }
            }

            //Do output if any output happened when sleeping
            if (inputStream.available() != 0) {
                final String outputFromSut = readFromSut(input);
                if (!testModelSimulation.runOutputAction(outputFromSut)) {
                    failed.add(testCase.getId());
                    return Verdict.FAIL;
                } else if (!mutantSimulation.runOutputAction(outputFromSut)) {
                    passed.add(testCase.getId());
                    return Verdict.PASS;
                }
            }

            return Verdict.NONE;
        } catch(InterruptedException | MutationTestingException | IOException e){
            e.printStackTrace();
            inconclusive.add(testCase.getId());
            //Todo stop testing and print error
            return Verdict.INCONCLUSIVE;
        }
    }

    /**
     * performs a delay on the simulations and itself and checks if the system under test made an output during the delay.
     * @param testModelSimulation simulation representing the test model.
     * @param mutantSimulation simulation representing the mutated model.
     * @param testCase that is being performed.
     * @return a verdict, it is NONE if no verdict were reached from this delay.
     */
    private Verdict delayForOutput(final SimpleComponentSimulation testModelSimulation, final SimpleComponentSimulation mutantSimulation, final MutationTestCase testCase, ObjectProperty<Instant> lastUpdateTime, final InputStream inputStream, final BufferedReader input){
        //Do delay until getOutputWaitTime time units has passed
        for(int i = 0; i < getPlan().getOutputWaitTime(); i++) {
            try {
                //Check if any output is ready, if there is none, do delay
                if (inputStream.available() == 0) {
                    Thread.sleep(timeUnit);
                    //Do Delay
                    final double waitedTimeUnits = Duration.between(lastUpdateTime.get(), Instant.now()).toMillis()/(double)timeUnit;
                    lastUpdateTime.setValue(Instant.now());
                    if (!testModelSimulation.delay(waitedTimeUnits)) {
                        failed.add(testCase.getId());
                        return Verdict.FAIL;
                    } else {
                        if (!mutantSimulation.delay(waitedTimeUnits)) {
                            //Todo Handle exception
                        }
                    }
                }

                //Do output if any output happened when sleeping
                if (inputStream.available() != 0) {
                    final String outputFromSut = readFromSut(input);
                    if (!testModelSimulation.runOutputAction(outputFromSut)) {
                        failed.add(testCase.getId());
                        return Verdict.FAIL;
                    } else if (!mutantSimulation.runOutputAction(outputFromSut)) {
                        passed.add(testCase.getId());
                        return Verdict.PASS;
                    }
                    return Verdict.NONE;
                }
            } catch (InterruptedException | MutationTestingException | IOException e) {
                e.printStackTrace();
                inconclusive.add(testCase.getId());
                //Todo stop testing and print error
                return Verdict.INCONCLUSIVE;
            }
        }
        inconclusive.add(testCase.getId());
        return Verdict.INCONCLUSIVE;
    }

    /**
     * Writes to the system.in of the system under test.
     * @param outputBroadcast the string to write to the system under test.
     */
    private void writeToSut(final String outputBroadcast, final BufferedWriter output, final Process sut) {
        try {
            //Write to process if it is alive, else act like the process accepts but ignore all inputs.
            if(sut.isAlive()) {
                output.write(outputBroadcast + "\n");
                output.flush();
            }
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads from the system under tests output stream.
     * @return the string read from the system under test.
     */
    private String readFromSut(BufferedReader input) {
        try {
            return input.readLine();
        } catch (final IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean shouldStop() {
        return getPlan().getStatus().equals(MutationTestPlan.Status.STOPPING) ||
                getPlan().getStatus().equals(MutationTestPlan.Status.ERROR);
    }

    @Override
    public void onStopped() { Platform.runLater(() -> getPlan().setStatus(MutationTestPlan.Status.IDLE)); }

    @Override
    public void onAllJobsSuccessfullyDone() {
        final Text text = new Text("Done");
        text.setFill(Color.GREEN);
        writeProgress(text);
        getPlan().setStatus(MutationTestPlan.Status.IDLE);
    }

    @Override
    public void writeProgress(final int jobsEnded) {
        writeProgress("Testcase: " + jobsEnded + "/" + mutationTestCases.size());
    }

    /**
     * Writes progress.
     * @param message the message describing the progress
     */
    private void writeProgress(final String message) {
        final Text text = new Text(message);
        text.setFill(Color.web("#333333"));
        writeProgress(text);
    }

    /**
     * Writes progress in a java fx thread.
     * @param text the text describing the progress
     */
    private void writeProgress(final Text text) {
        Platform.runLater(() -> getProgressWriter().accept(text));
    }

    @Override
    public int getMaxConcurrentJobs() {
        return getPlan().getConcurrentSutInstances();
    }

    @Override
    public void startJob(final int index) {
        performTest(mutationTestCases.get(index));
    }

    private Consumer<Text> getProgressWriter() { return progressWriterText; }

    private MutationTestPlan getPlan() {return testPlan; }
}