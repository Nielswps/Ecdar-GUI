package SW9.controllers;

import SW9.Ecdar;
import SW9.abstractions.Component;
import SW9.presentations.DropDownMenu;
import SW9.presentations.FilePresentation;
import SW9.utility.UndoRedoStack;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXRippler;
import com.jfoenix.controls.JFXTextArea;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ResourceBundle;

public class ProjectPaneController implements Initializable {

    private final HashMap<Component, FilePresentation> componentPresentationMap = new HashMap<>();
    public StackPane root;
    public AnchorPane toolbar;
    public Label toolbarTitle;
    public ScrollPane scrollPane;
    public VBox filesList;
    public JFXRippler createComponent;
    public VBox mainComponentContainer;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {

        Ecdar.getProject().getComponents().addListener(new ListChangeListener<Component>() {
            @Override
            public void onChanged(final Change<? extends Component> c) {
                while (c.next()) {
                    c.getAddedSubList().forEach(o -> handleAddedComponent(o));
                    c.getRemoved().forEach(o -> handleRemovedComponent(o));

                    // We should make a new component active
                    if (c.getRemoved().size() > 0) {
                        if (Ecdar.getProject().getComponents().size() > 0) {
                            // Find the first available component and show it instead of the removed one
                            final Component component = Ecdar.getProject().getComponents().get(0);
                            CanvasController.setActiveComponent(component);
                        } else {
                            // Show no components (since there are none in the project)
                            CanvasController.setActiveComponent(null);
                        }
                    }

                    // Sort the children alphabetically
                    sortPresentations();
                }
            }
        });

        Ecdar.getProject().getComponents().forEach(this::handleAddedComponent);
    }

    private void sortPresentations() {
        final ArrayList<Component> sortedComponentList = new ArrayList<>();
        componentPresentationMap.keySet().forEach(sortedComponentList::add);
        sortedComponentList.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));
        sortedComponentList.forEach(component -> componentPresentationMap.get(component).toFront());
    }

    private void initializeColorSelector(final FilePresentation filePresentation) {
        final JFXRippler moreInformation = (JFXRippler) filePresentation.lookup("#moreInformation");
        final int listWidth = 230;
        final DropDownMenu moreInformationDropDown = new DropDownMenu(root, moreInformation, listWidth, true);
        final Component component = filePresentation.getComponent();

        moreInformationDropDown.addListElement("Configuration");

        /*
         * IS MAIN
         */
        moreInformationDropDown.addTogglableListElement("Main", filePresentation.getComponent().isMainProperty(), event -> {
            final boolean wasMain = component.isIsMain();

            UndoRedoStack.push(() -> { // Perform
                component.setIsMain(!wasMain);
            }, () -> { // Undo
                component.setIsMain(wasMain);
            }, "Component " + component.getName() + " isMain: " + !wasMain, "star");
        });

        /*
         * INCLUDE IN PERIODIC CHECK
         */
        moreInformationDropDown.addTogglableListElement("Include in periodic check", component.includeInPeriodicCheckProperty(), event -> {
            final boolean didIncludeInPeriodicCheck = component.includeInPeriodicCheckProperty().get();

            UndoRedoStack.push(() -> { // Perform
                component.includeInPeriodicCheckProperty().set(!didIncludeInPeriodicCheck);
            }, () -> { // Undo
                component.includeInPeriodicCheckProperty().set(didIncludeInPeriodicCheck);
            }, "Component " + component.getName() + " is included in periodic check: " + !didIncludeInPeriodicCheck, "search");
        });

        moreInformationDropDown.addSpacerElement();

        moreInformationDropDown.addListElement("Description");

        final JFXTextArea textArea = new JFXTextArea();
        textArea.setMinHeight(30);

        filePresentation.getComponent().descriptionProperty().bindBidirectional(textArea.textProperty());

        textArea.textProperty().addListener((obs, oldText, newText) -> {
            int i = 0;
            for (final char c : newText.toCharArray()) {
                if (c == '\n') {
                    i++;
                }
            }

            textArea.setMinHeight(i * 17 + 30);
        });

        moreInformationDropDown.addCustomChild(textArea);

        moreInformationDropDown.addSpacerElement();

        moreInformationDropDown.addListElement("Color");

        /*
         * COLOR SELECTOR
         */
        moreInformationDropDown.addColorPicker(filePresentation.getComponent(), filePresentation.getComponent()::color);

        moreInformationDropDown.addSpacerElement();

        /*
         * THE DELETE BUTTON
         */
        moreInformationDropDown.addClickableListElement("Delete", event -> {
            UndoRedoStack.push(() -> { // Perform
                Ecdar.getProject().getComponents().remove(component);
            }, () -> { // Undo
                Ecdar.getProject().getComponents().add(component);
            }, "Deleted component " + component.getName(), "delete");

            moreInformationDropDown.close();
        });

        moreInformation.setOnMousePressed((e) -> {
            e.consume();
            moreInformationDropDown.show(JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT, 10, 10);
        });
    }

    private void handleAddedComponent(final Component component) {
        final FilePresentation filePresentation = new FilePresentation(component);
        initializeColorSelector(filePresentation);
        filesList.getChildren().add(filePresentation);
        componentPresentationMap.put(component, filePresentation);

        // Open the component if the presentation is pressed
        filePresentation.setOnMousePressed(event -> {
            event.consume();
            CanvasController.setActiveComponent(component);
        });

        component.nameProperty().addListener(obs -> sortPresentations());

        component.isMainProperty().addListener((obs, oldIsMain, newIsMain) -> {
            final Component mainComponent = Ecdar.getProject().getMainComponent();

            if (component.equals(mainComponent) && !newIsMain) {
                Ecdar.getProject().setMainComponent(null);
                return;
            }

            if (mainComponent != null && newIsMain) {
                mainComponent.setIsMain(false);
            }

            Ecdar.getProject().setMainComponent(component);
        });
    }

    private void handleRemovedComponent(final Component component) {
        filesList.getChildren().remove(componentPresentationMap.get(component));
        componentPresentationMap.remove(component);
    }

    @FXML
    private void createComponentClicked() {
        final Component newComponent = new Component(true);

        UndoRedoStack.push(() -> { // Perform
            Ecdar.getProject().getComponents().add(newComponent);
        }, () -> { // Undo
            Ecdar.getProject().getComponents().remove(newComponent);
        }, "Created new component: " + newComponent.getName(), "add-circle");

        CanvasController.setActiveComponent(newComponent);
    }

}