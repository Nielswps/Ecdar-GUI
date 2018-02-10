package ecdar.mutation;

import ecdar.Ecdar;
import ecdar.abstractions.HighLevelModelObject;
import com.google.gson.JsonObject;
import javafx.beans.property.*;

import java.util.HashSet;

/**
 * A test plan for conducting model-based mutation testing on a component.
 */
public class MutationTestPlan extends HighLevelModelObject {
    private static final String PLAN_NAME_PREFIX = "Test";

    private static final String TEST_MODEL_ID = "testModelId";
    private static final String MUTANTS_TEXT = "mutantsText";
    private static final String TEST_CASES_TEXT = "testCasesText";

    public enum Status {IDLE, WORKING, STOPPING}

    private final StringProperty testModelId = new SimpleStringProperty("");
    private final StringProperty action = new SimpleStringProperty("");
    private final StringProperty sutPath = new SimpleStringProperty("");
    private final StringProperty format = new SimpleStringProperty("");
    private final BooleanProperty demonic = new SimpleBooleanProperty(false);
    private final BooleanProperty angelicWhenExport = new SimpleBooleanProperty(true);

    private final StringProperty mutantsString = new SimpleStringProperty("");
    private final StringProperty testCasesString = new SimpleStringProperty("");

    private final ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.IDLE);


    /* Constructors */

    public MutationTestPlan() {
        generateName();
    }

    public MutationTestPlan(final JsonObject json) {
        deserialize(json);
    }


    /* Properties */

    public String getTestModelId() {
        return testModelId.get();
    }

    public StringProperty testModelIdProperty() {
        return testModelId;
    }

    public void setTestModelId(final String testModelId) {
        this.testModelId.setValue(testModelId);
    }

    public String getMutantsString() {
        return mutantsString.get();
    }

    public StringProperty mutantsTextProperty() {
        return mutantsString;
    }

    public void setMutantsText(final String value) {
        mutantsString.set(value);
    }

    public String getTestCasesString() {
        return testCasesString.get();
    }

    public StringProperty testCasesTextProperty() {
        return testCasesString;
    }

    public void setTestCasesText(final String value) {
        testCasesString.set(value);
    }


    /* Other methods */

    @Override
    public JsonObject serialize() {
        final JsonObject result = super.serialize();

        result.addProperty(TEST_MODEL_ID, getTestModelId());
        result.addProperty(MUTANTS_TEXT, getMutantsString());
        result.addProperty(TEST_CASES_TEXT, getTestCasesString());

        return result;
    }

    @Override
    public void deserialize(final JsonObject json) {
        super.deserialize(json);

        setTestModelId(json.getAsJsonPrimitive(TEST_MODEL_ID).getAsString());
        setMutantsText(json.getAsJsonPrimitive(MUTANTS_TEXT).getAsString());
        setTestCasesText(json.getAsJsonPrimitive(TEST_CASES_TEXT).getAsString());
    }


    /**
     * Generate and sets a unique id for this system
     */
    private void generateName() {
        final HashSet<String> names = new HashSet<>();

        for (final MutationTestPlan plan : Ecdar.getProject().getTestPlans()){
            names.add(plan.getName());
        }

        for (int counter = 1; ; counter++) {
            if (!names.contains(PLAN_NAME_PREFIX + counter)){
                setName((PLAN_NAME_PREFIX + counter));
                return;
            }
        }
    }

    public String getAction() {
        return action.get();
    }

    public StringProperty actionProperty() {
        return action;
    }

    public void setAction(final String value) {
        action.set(value);
    }

    public String getSutPath() {
        return sutPath.get();
    }

    public StringProperty sutPathProperty() {
        return sutPath;
    }

    public void setSutPath(final String value) {
        sutPath.set(value);
    }

    public String getFormat() {
        return format.get();
    }

    public StringProperty formatProperty() {
        return format;
    }

    public void setFormat(final String value) {
        format.set(value);
    }

    public boolean isDemonic() {
        return demonic.get();
    }

    public BooleanProperty demonicProperty() {
        return demonic;
    }

    public void setDemonic(final boolean value) {
        demonic.set(value);
    }

    public boolean isAngelicWhenExport() {
        return angelicWhenExport.get();
    }

    public BooleanProperty angelicWhenExportProperty() {
        return angelicWhenExport;
    }

    public void setAngelicWhenExport(final boolean value) {
        angelicWhenExport.set(value);
    }

    public Status getStatus() {
        return status.get();
    }

    public ObjectProperty<Status> statusProperty() {
        return status;
    }

    public void setStatus(final Status value) {
        status.set(value);
    }
}
