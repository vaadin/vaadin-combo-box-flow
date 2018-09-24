package com.vaadin.flow.component.combobox;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.ItemLabelGenerator;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dependency.HtmlImport;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.data.binder.HasFilterableDataProvider;
import com.vaadin.flow.data.provider.ArrayUpdater;
import com.vaadin.flow.data.provider.ArrayUpdater.Update;
import com.vaadin.flow.data.provider.CompositeDataGenerator;
import com.vaadin.flow.data.provider.DataCommunicator;
import com.vaadin.flow.data.provider.DataKeyMapper;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.renderer.Rendering;
import com.vaadin.flow.data.renderer.TemplateRenderer;
import com.vaadin.flow.dom.DisabledUpdateMode;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.function.SerializableBiPredicate;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.function.ValueProvider;
import com.vaadin.flow.internal.JsonUtils;
import com.vaadin.flow.shared.Registration;

import elemental.json.JsonValue;

@HtmlImport("frontend://flow-component-renderer.html")
@HtmlImport("frontend://b_components/vaadin-combo-box/src/vaadin-combo-box.html")
@JavaScript("frontend://comboBoxConnector.js")
public class ComboBox<T> extends GeneratedVaadinComboBox<ComboBox<T>, T>
        implements HasSize, HasValidation,
        HasFilterableDataProvider<T, String> {

    private static final String ITEM_LABEL_PROPERTY = "label";
    private static final String KEY_PROPERTY = "key";
    private static final String SELECTED_ITEM_PROPERTY_NAME = "selectedItem";
    private static final String VALUE_PROPERTY_NAME = "value";

    private final class UpdateQueue implements Update {
        private List<Runnable> queue = new ArrayList<>();

        private UpdateQueue(int size) {
            enqueue("$connector.updateSize", size);
            // getElement().setProperty("size", size);
        }

        @Override
        public void set(int start, List<JsonValue> items) {
            System.out.println("--------------");
            System.out.println(start);
            System.out.println(items.size());
            enqueue("$connector.set", start,
                    items.stream().collect(JsonUtils.asArray()));
        }

        @Override
        public void clear(int start, int length) {
            enqueue("$connector.clear", start, length);
        }

        @Override
        public void commit(int updateId) {
            // dataCommunicator.confirmUpdate(updateId);
            enqueue("$connector.confirm", updateId);
            queue.forEach(Runnable::run);
            queue.clear();
        }

        private void enqueue(String name, Serializable... arguments) {
            queue.add(() -> getElement().callFunction(name, arguments));
        }
    }

    private final ArrayUpdater arrayUpdater = new ArrayUpdater() {
        @Override
        public Update startUpdate(int sizeChange) {
            return new UpdateQueue(sizeChange);
        }

        @Override
        public void initialize() {
            initConnector();
        }
    };

    private final class EagerUpdateQueue implements Update {

        private EagerUpdateQueue(int size) {
            getElement().setProperty("size", size);
        }

        @Override
        public void set(int start, List<JsonValue> items) {
            System.out.println("--------------");
            System.out.println(start);
            System.out.println(items.size());
            setItems(items.stream().collect(JsonUtils.asArray()));
        }

        @Override
        public void clear(int start, int length) {
        }

        @Override
        public void commit(int updateId) {
        }
    }

    private final ArrayUpdater eagerArrayUpdater = new ArrayUpdater() {
        @Override
        public Update startUpdate(int sizeChange) {
            return new EagerUpdateQueue(sizeChange);
        }

        @Override
        public void initialize() {
        }
    };

    private ItemLabelGenerator<T> itemLabelGenerator = String::valueOf;

    private Renderer<T> renderer;

    private final CompositeDataGenerator<T> dataGenerator = new CompositeDataGenerator<>();
    private Registration dataGeneratorRegistration;

    private Element template;

    private int customValueListenersCount;

    private String nullRepresentation = "";

    private DataCommunicator<T> dataCommunicator;

    public ComboBox() {
        super(null, null, String.class, ComboBox::presentationToModel,
                ComboBox::modelToPresentation);
        dataGenerator.addDataGenerator((item, jsonObject) -> jsonObject
                .put("label", item == null ? nullRepresentation
                        : itemLabelGenerator.apply(item)));

        getElement().setProperty("itemValuePath", "key");
    }

    /**
     * Creates an empty combo box with the defined label.
     *
     * @param label
     *            the label describing the combo box
     */
    public ComboBox(String label) {
        this();
        setLabel(label);
    }

    /**
     * Creates an empty combo box with the defined page size for lazy loading.
     * <p>
     * Note that the page size takes effect only when defining a data provider
     * for the combo box.
     * 
     * @param pageSize
     *            the amount of items to request at a time for lazy loading
     */
    public ComboBox(int pageSize) {
        this();
        setPageSize(pageSize);
    }

    /**
     * Creates a combo box with the defined label and populated with the items
     * in the collection.
     *
     * @param label
     *            the label describing the combo box
     * @param items
     *            the items to be shown in the list of the combo box
     * @see #setItems(Collection)
     */
    public ComboBox(String label, Collection<T> items) {
        this();
        setLabel(label);
        setItems(items);
    }

    /**
     * Creates a combo box with the defined label and populated with the items
     * in the array.
     *
     * @param label
     *            the label describing the combo box
     * @param items
     *            the items to be shown in the list of the combo box
     * @see #setItems(Object...)
     */
    @SafeVarargs
    public ComboBox(String label, T... items) {
        this();
        setLabel(label);
        setItems(items);
    }

    public String getFilterString() {
        return super.getFilterString();
    }

    private static <T> T presentationToModel(ComboBox<T> comboBox,
            String presentation) {
        if (presentation != null) {
            return comboBox.getKeyMapper().get(presentation);
        }
        return comboBox.getEmptyValue();
    }

    private static <T> String modelToPresentation(ComboBox<T> comboBox,
            T model) {
        return comboBox.getKeyMapper().key(model);
    }

    @Override
    public T getValue() {
        /* Internally we use the key property as the combobox's value */
        String key = getElement().getProperty("value");
        return presentationToModel(this, key);
    }

    public void setRenderer(ValueProvider<T, String> valueProvider) {
        Objects.requireNonNull(valueProvider,
                "The valueProvider must not be null");
        setRenderer(TemplateRenderer.<T> of("[[item.label]]")
                .withProperty("label", valueProvider));
    }

    public void setRenderer(Renderer<T> renderer) {
        Objects.requireNonNull(renderer, "The renderer must not be null");
        this.renderer = renderer;

        if (template == null) {
            template = new Element("template");
            getElement().appendChild(template);
        }

        // if (dataGeneratorRegistration != null) {
        // dataGeneratorRegistration.remove();
        // dataGeneratorRegistration = null;
        // }
        // if (dataCommunicator != null) {
        // Rendering<T> rendering = renderer.render(getElement(),
        // dataCommunicator.getKeyMapper(), template);
        // if (rendering.getDataGenerator().isPresent()) {
        // dataGeneratorRegistration = dataGenerator
        // .addDataGenerator(rendering.getDataGenerator().get());
        // }
        //
        // dataCommunicator.reset();
        // }
        render();
    }

    private void render() {
        if (dataCommunicator == null || renderer == null) {
            return;
        }
        runBeforeClientResponse(ui -> {
            if (dataGeneratorRegistration != null) {
                dataGeneratorRegistration.remove();
                dataGeneratorRegistration = null;
            }
            Rendering<T> rendering = renderer.render(getElement(),
                    dataCommunicator.getKeyMapper(), template);
            if (rendering.getDataGenerator().isPresent()) {
                dataGeneratorRegistration = dataGenerator
                        .addDataGenerator(rendering.getDataGenerator().get());
            }
            dataCommunicator.reset();
        });
    }

    @ClientCallable(DisabledUpdateMode.ALWAYS)
    private void confirmUpdate(int id) {
        dataCommunicator.confirmUpdate(id);
    }

    @ClientCallable(DisabledUpdateMode.ALWAYS)
    private void setRequestedRange(int start, int length) {
        dataCommunicator.setRequestedRange(start, length);
    }

    void runBeforeClientResponse(SerializableConsumer<UI> command) {
        getElement().getNode().runWhenAttached(ui -> ui
                .beforeClientResponse(this, context -> command.accept(ui)));
    }

    private void initConnector() {
        getUI().orElseThrow(() -> new IllegalStateException(
                "Connector can only be initialized for an attached ComboBox"))
                .getPage().executeJavaScript(
                        "window.Vaadin.Flow.comboBoxConnector.initLazy($0)",
                        getElement());
    }

    private boolean eager;

    @Override
    public void setItems(Collection<T> items) {
        eager = true;

        dataCommunicator = new DataCommunicator<>(dataGenerator,
                eagerArrayUpdater, data -> getElement()
                        .callFunction("$connector.updateData", data),
                getElement().getNode());
        ListDataProvider<T> listDataProvider = new ListDataProvider<T>(items) {
            // Allow null values
            public Object getId(T item) {
                return item;
            };
        };
        setDataProvider(listDataProvider);

        dataCommunicator.setRequestedRange(0, items.size());
        render();
    }

    @Override
    public <C> void setDataProvider(DataProvider<T, C> dataProvider,
            SerializableFunction<String, C> filterConverter) {
        Objects.requireNonNull(dataProvider, "dataProvider cannot be null");
        Objects.requireNonNull(filterConverter,
                "filterConverter cannot be null");

        if (dataCommunicator == null)
            dataCommunicator = new DataCommunicator<>(dataGenerator,
                    arrayUpdater, data -> getElement()
                            .callFunction("$connector.updateData", data),
                    getElement().getNode());

        if (renderer != null) {
            setRenderer(renderer);
        }

        SerializableFunction<String, C> convertOrNull = filterText -> {
            if (filterText == null || filterText.isEmpty()) {
                return null;
            }

            return filterConverter.apply(filterText);
        };

        SerializableConsumer<C> providerFilterSlot = dataCommunicator
                .setDataProvider(dataProvider,
                        convertOrNull.apply(getFilterString()));

        filterSlot = filter -> providerFilterSlot
                .accept(convertOrNull.apply(filter));

        render();

        if (eager) {
            return;
        }
        getElement().addEventListener("filter-changed", event -> {
            filterSlot.accept(getFilterString());
        }).debounce(200);

    }

    private SerializableConsumer<String> filterSlot = filter -> {
        // Just ignore when neither setDataProvider nor setItems has been called
    };

    public void setDataProvider(ListDataProvider<T> listDataProvider) {
        // Cannot use the case insensitive contains shorthand from
        // ListDataProvider since it wouldn't react to locale changes
        LabelFilter defaultLabelFilter = (itemText, filterText) -> itemText
                .toLowerCase(getLocale())
                .contains(filterText.toLowerCase(getLocale()));

        setDataProvider(defaultLabelFilter, listDataProvider);
    }

    public void setDataProvider(LabelFilter labelFilter,
            ListDataProvider<T> listDataProvider) {
        Objects.requireNonNull(listDataProvider,
                "List data provider cannot be null");

        // Must do getItemLabelGenerator() for each operation since it might
        // not be the same as when this method was invoked
        setDataProvider(listDataProvider, filterText -> item -> labelFilter
                .test(getItemLabelGenerator().apply(item), filterText));
    }

    /**
     * Sets the item label generator that is used to produce the strings shown
     * in the combo box for each item. By default,
     * {@link String#valueOf(Object)} is used.
     * <p>
     * When the {@link #setRenderer(Renderer)} is used, the ItemLabelGenerator
     * is only used to show the selected item label.
     *
     * @param itemLabelGenerator
     *            the item label provider to use, not null
     */
    public void setItemLabelGenerator(
            ItemLabelGenerator<T> itemLabelGenerator) {
        Objects.requireNonNull(itemLabelGenerator,
                "The item label generator can not be null");
        this.itemLabelGenerator = itemLabelGenerator;
    }

    public ItemLabelGenerator<T> getItemLabelGenerator() {
        return itemLabelGenerator;
    }

    @Override
    public T getEmptyValue() {
        return null;
    }

    public DataProvider<T, ?> getDataProvider() {
        return dataCommunicator.getDataProvider();
    }

    private DataKeyMapper<T> getKeyMapper() {
        return dataCommunicator.getKeyMapper();
    }

    @Override
    public void setOpened(boolean opened) {
        super.setOpened(opened);
    }

    /**
     * Gets the states of the drop-down.
     *
     * @return {@code true} if the drop-down is opened, {@code false} otherwise
     */
    public boolean isOpened() {
        return isOpenedBoolean();
    }

    @Override
    public void setInvalid(boolean invalid) {
        super.setInvalid(invalid);
    }

    /**
     * Gets the validity of the combobox output.
     * <p>
     * return true, if the value is invalid.
     *
     * @return the {@code validity} property from the component
     */
    @Override
    public boolean isInvalid() {
        return isInvalidBoolean();
    }

    @Override
    public void setErrorMessage(String errorMessage) {
        super.setErrorMessage(errorMessage);
    }

    /**
     * Gets the current error message from the combobox.
     *
     * @return the current error message
     */
    @Override
    public String getErrorMessage() {
        return getErrorMessageString();
    }

    @Override
    public void setAllowCustomValue(boolean allowCustomValue) {
        super.setAllowCustomValue(allowCustomValue);
    }

    /**
     * If {@code true}, the user can input a value that is not present in the
     * items list. {@code value} property will be set to the input value in this
     * case. Also, when {@code value} is set programmatically, the input value
     * will be set to reflect that value.
     * <p>
     * This property is not synchronized automatically from the client side, so
     * the returned value may not be the same as in client side.
     * </p>
     *
     * @return the {@code allowCustomValue} property from the combobox
     */
    public boolean isAllowCustomValue() {
        return isAllowCustomValueBoolean();
    }

    /**
     * Set the combobox to be input focused when the page loads.
     *
     * @param autofocus
     *            the boolean value to set
     */
    @Override
    public void setAutofocus(boolean autofocus) {
        super.setAutofocus(autofocus);
    }

    /**
     * Get the state for the auto-focus property of the combobox.
     * <p>
     * This property is not synchronized automatically from the client side, so
     * the returned value may not be the same as in client side.
     *
     * @return the {@code autofocus} property from the combobox
     */
    public boolean isAutofocus() {
        return isAutofocusBoolean();
    }

    @Override
    public void setPreventInvalidInput(boolean preventInvalidInput) {
        super.setPreventInvalidInput(preventInvalidInput);
    }

    /**
     * Determines whether preventing the user from inputing invalid value.
     * <p>
     * This property is not synchronized automatically from the client side, so
     * the returned value may not be the same as in client side.
     *
     * @return the {@code preventInvalidInput} property of the combobox
     */
    public boolean isPreventInvalidInput() {
        return isPreventInvalidInputBoolean();
    }

    @Override
    public void setRequired(boolean required) {
        super.setRequired(required);
    }

    /**
     * Determines whether the combobox is marked as input required.
     * <p>
     * This property is not synchronized automatically from the client side, so
     * the returned value may not be the same as in client side.
     *
     * @return {@code true} if the input is required, {@code false} otherwise
     */
    public boolean isRequired() {
        return isRequiredBoolean();
    }

    @Override
    public void setLabel(String label) {
        super.setLabel(label);
    }

    /**
     * Gets the label of the combobox.
     *
     * @return the {@code label} property of the combobox
     */
    public String getLabel() {
        return getLabelString();
    }

    @Override
    public void setPlaceholder(String placeholder) {
        super.setPlaceholder(placeholder);
    }

    /**
     * Gets the placeholder of the combobox.
     *
     * @return the {@code placeholder} property of the combobox
     */
    public String getPlaceholder() {
        return getPlaceholderString();
    }

    @Override
    public void setPattern(String pattern) {
        super.setPattern(pattern);
    }

    /**
     * Gets the valid input pattern
     *
     * @return the {@code pattern} property of the combobox
     */
    public String getPattern() {
        return getPatternString();
    }

    /**
     * Adds a listener for CustomValueSetEvent which is fired when user types in
     * a value that don't already exist in the ComboBox.
     *
     * <p>
     * As a side effect makes the ComboBox allow custom values. If you don't
     * want to allow a user to add new values to the list once the listener is
     * added please disable it explicitly via the
     * {@link #setAllowCustomValue(boolean)} method.
     * </p>
     *
     * <p>
     * The custom value becomes disallowed automatically once the last custom
     * value set listener is removed.
     * </p>
     *
     * @see #setAllowCustomValue(boolean)
     *
     * @param listener
     *            the listener to be notified when a new value is filled
     * @return a {@link Registration} for removing the event listener
     */
    // @Override
    // public Registration addCustomValueSetListener(
    // ComponentEventListener<CustomValueSetEvent<ComboBox<T>>> listener) {
    // setAllowCustomValue(true);
    // customValueListenersCount++;
    // Registration registration = super.addCustomValueSetListener(listener);
    // return new CustomValueRegistraton(registration);
    // }

    /**
     * Sets representation string for UI display, when the item is null.
     * 
     * <p>
     * By default, the null field value will be shown as empty string.
     * 
     * @param label
     *            the string to be set
     */
    public void setNullRepresentation(String label) {
        Objects.requireNonNull(label,
                "The null representation should not be null.");
        nullRepresentation = label;
    }

    /**
     * Gets the null representation string.
     * 
     * @return the string represents the null item in the ComboBox
     */
    public String getNullRepresentation() {
        return nullRepresentation;
    }

    private void setPageSize(int pageSize) {
        getElement().setProperty("pageSize", pageSize);
    }

    /**
     * Predicate to check {@link ComboBox} items against user typed strings.
     */
    @FunctionalInterface
    public interface LabelFilter
            extends SerializableBiPredicate<String, String> {
        @Override
        public boolean test(String itemCaption, String filterText);
    }

}
