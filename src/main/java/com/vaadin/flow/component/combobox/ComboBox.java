/*
 * Copyright 2000-2018 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.flow.component.combobox;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.ItemLabelGenerator;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dependency.HtmlImport;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.data.binder.HasFilterableDataProvider;
import com.vaadin.flow.data.provider.ArrayUpdater;
import com.vaadin.flow.data.provider.ArrayUpdater.Update;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.CompositeDataGenerator;
import com.vaadin.flow.data.provider.DataCommunicator;
import com.vaadin.flow.data.provider.DataKeyMapper;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.renderer.Rendering;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.function.SerializableBiPredicate;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.internal.JsonUtils;
import com.vaadin.flow.shared.Registration;

import elemental.json.Json;
import elemental.json.JsonArray;
import elemental.json.JsonObject;
import elemental.json.JsonValue;

/**
 * Server-side component for the {@code vaadin-combo-box} webcomponent. It
 * contains the same features of the webcomponent, such as item filtering,
 * object selection and item templating.
 * <p>
 * You can provide data for the component either eagerly or lazily. To send data
 * eagerly, use {@link #setItems(Collection)} or its overload. This method is
 * suitable for a small set of options, and it enables the more responsive
 * client-side filtering.
 * <p>
 * For lazy loading data as the user scrolls down the component, use
 * {@link #setDataProvider(DataProvider)} or its overloads. This method is
 * suitable for large data sets, to avoid unnecessary network traffic. The
 * filtering will happen in server-side, and you can override the default filter
 * strategy with {@link #setDataProvider(ItemFilter, ListDataProvider)}.
 *
 * @param <T>
 *            the type of the items to be inserted in the combo box
 */
@HtmlImport("frontend://flow-component-renderer.html")
@JavaScript("frontend://comboBoxConnector.js")
@SuppressWarnings("serial")
public class ComboBox<T> extends GeneratedVaadinComboBox<ComboBox<T>, T>
        implements HasSize, HasValidation,
        HasFilterableDataProvider<T, String> {

    /**
     * A callback method for fetching items. The callback is provided with a
     * non-null string filter, offset index and limit.
     *
     * @param <T>
     *            item (bean) type in ComboBox
     */
    @FunctionalInterface
    public interface FetchItemsCallback<T> extends Serializable {

        /**
         * Returns a stream of items that match the given filter, limiting the
         * results with given offset and limit.
         *
         * @param filter
         *            a non-null filter string
         * @param offset
         *            the first index to fetch
         * @param limit
         *            the fetched item count
         * @return stream of items
         */
        public Stream<T> fetchItems(String filter, int offset, int limit);
    }

    private class CustomValueRegistration implements Registration {

        private Registration delegate;

        private CustomValueRegistration(Registration delegate) {
            this.delegate = delegate;
        }

        @Override
        public void remove() {
            if (delegate != null) {
                delegate.remove();
                customValueListenersCount--;

                if (customValueListenersCount == 0) {
                    setAllowCustomValue(false);
                }
                delegate = null;
            }
        }
    }

    private final class UpdateQueue implements Update {
        private List<Runnable> queue = new ArrayList<>();

        private UpdateQueue(int size) {
            enqueue("$connector.updateSize", size);
        }

        @Override
        public void set(int start, List<JsonValue> items) {
            enqueue("$connector.set", start,
                    items.stream().collect(JsonUtils.asArray()));
        }

        @Override
        public void clear(int start, int length) {
            if (length > 0) {
                enqueue("clearCache");
            }
        }

        @Override
        public void commit(int updateId) {
            enqueue("$connector.confirm", updateId);
            queue.forEach(Runnable::run);
            queue.clear();
        }

        private void enqueue(String name, Serializable... arguments) {
            queue.add(() -> getElement().callFunction(name, arguments));
        }
    }

    /**
     * Lazy loading updater, used when calling setDataProvider()
     */
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

    /**
     * Updater which just sets the items-property eagerly for the web component.
     * This is done to allow re-using the dataprovider design for the eager
     * use-case. This is used when calling setItems().
     */
    private final ArrayUpdater eagerArrayUpdater = new ArrayUpdater() {
        @Override
        public Update startUpdate(int sizeChange) {
            getElement().setProperty("size", sizeChange);
            return new Update() {
                @Override
                public void clear(int start, int length) {
                    setItems(Json.createArray());
                }

                @Override
                public void set(int start, List<JsonValue> items) {
                    setItems(items.stream().collect(JsonUtils.asArray()));
                }

                @Override
                public void commit(int updateId) {
                    // NO-OP
                }
            };
        }

        @Override
        public void initialize() {
            // NO-OP
        }
    };

    /**
     * Predicate to check {@link ComboBox} items against user typed strings.
     */
    @FunctionalInterface
    public interface ItemFilter<T> extends SerializableBiPredicate<T, String> {
        @Override
        public boolean test(T item, String filterText);
    }

    private ItemLabelGenerator<T> itemLabelGenerator = String::valueOf;

    private Renderer<T> renderer;
    private boolean renderScheduled;

    private DataCommunicator<T> dataCommunicator;
    private final CompositeDataGenerator<T> dataGenerator = new CompositeDataGenerator<>();
    private Registration dataGeneratorRegistration;

    private enum LoadMode {
        EAGER, // when using setItems()
        LAZY // when using setDataProvider()
    };

    private LoadMode loadMode;
    private boolean settingItems;

    private Element template;

    private int customValueListenersCount;

    private String nullRepresentation = "";

    private ArrayList<T> temporaryFilteredItems;

    private SerializableConsumer<String> filterSlot = filter -> {
        // Just ignore when setDataProvider has not been called
    };

    private Registration filterChangeRegistration;

    /**
     * Default constructor. Creates an empty combo box.
     */
    public ComboBox() {
        super(null, null, String.class, ComboBox::presentationToModel,
                ComboBox::modelToPresentation);
        dataGenerator.addDataGenerator((item, jsonObject) -> jsonObject
                .put("label", generateLabel(item)));

        getElement().setProperty("itemValuePath", "key");
        getElement().setProperty("itemIdPath", "key");
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
     * Note that the page size takes effect only when setting a data provider
     * for the combo box. If you are using {@link #setItems(Collection)}, all
     * the items are sent eagerly to the client.
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

    private static <T> T presentationToModel(ComboBox<T> comboBox,
            String presentation) {
        if (presentation == null || comboBox.dataCommunicator == null) {
            return comboBox.getEmptyValue();
        }
        return comboBox.getKeyMapper().get(presentation);
    }

    private static <T> String modelToPresentation(ComboBox<T> comboBox,
            T model) {
        if (model == null) {
            return null;
        }

        if (comboBox.loadMode == LoadMode.EAGER) {
            ListDataProvider<T> dp = ((ListDataProvider<T>) comboBox
                    .getDataProvider());
            Collection<T> items = dp.getItems();
            Optional<T> item = items.stream().filter(
                    object -> Objects.equals(dp.getId(model), dp.getId(object)))
                    .findFirst();

            if (!item.isPresent()) {
                throw new IllegalArgumentException(
                        "The provided value is not part of ComboBox: " + model);
            }
        }
        return comboBox.getKeyMapper().key(model);
    }

    @Override
    public void setValue(T value) {
        super.setValue(value);

        // This ensures that the selection works even with lazy loading when the
        // item is not yet loaded
        getElement().setPropertyJson("selectedItem", generateData(value));
    }

    /**
     * Sets the TemplateRenderer responsible to render the individual items in
     * the list of possible choices of the ComboBox. It doesn't affect how the
     * selected item is rendered - that can be configured by using
     * {@link #setItemLabelGenerator(ItemLabelGenerator)}.
     *
     * @param renderer
     *            a renderer for the items in the selection list of the
     *            ComboBox, not <code>null</code>
     */
    public void setRenderer(Renderer<T> renderer) {
        Objects.requireNonNull(renderer, "The renderer must not be null");
        this.renderer = renderer;

        if (template == null) {
            template = new Element("template");
            getElement().appendChild(template);
        }
        render();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method sends all of the items to the browser at once, so it should
     * be used only with relatively small data sets. The benefit of using this
     * method is that the filtering will happen responsively in the client-side.
     * For large data-sets, you should use lazy loading via
     * {@link #setDataProvider(DataProvider)}.
     */
    @Override
    public void setItems(Collection<T> items) {
        ListDataProvider<T> listDataProvider = new ListDataProvider<T>(items) {
            // Allow null values
            @Override
            public Object getId(T item) {
                return item;
            };
        };
        setItems(listDataProvider);
    }

    /**
     * Sets the items of this component from the given data provider.
     * <p>
     * This method sends all of the items to the browser at once, so it should
     * be used only with relatively small data sets. The benefit of using this
     * method is that the filtering will happen responsively in the client-side.
     * For large data-sets, you should use lazy loading via
     * {@link #setDataProvider(DataProvider)}.
     * 
     * @param dataProvider
     *            the data provider which will populate the ComboBox eagerly
     */
    public void setItems(ListDataProvider<T> dataProvider) {
        if (loadMode == LoadMode.LAZY) {
            throwWhenChangingBetweenEagerAndLazy();
        }
        loadMode = LoadMode.EAGER;

        dataCommunicator = new DataCommunicator<T>(dataGenerator,
                eagerArrayUpdater, data -> {
                    // Updating data not implemented
                }, getElement().getNode());

        settingItems = true;
        setDataProvider(dataProvider);
        setValue(null);

        dataCommunicator.setRequestedRange(0, dataProvider.getItems().size());
        render();
    }

    @Override
    public <C> void setDataProvider(DataProvider<T, C> dataProvider,
            SerializableFunction<String, C> filterConverter) {
        Objects.requireNonNull(dataProvider,
                "The data provider can not be null");
        Objects.requireNonNull(filterConverter,
                "filterConverter cannot be null");

        if (loadMode == LoadMode.EAGER && !settingItems) {
            throwWhenChangingBetweenEagerAndLazy();
        }

        if (dataCommunicator == null) {
            loadMode = LoadMode.LAZY;
            dataCommunicator = new DataCommunicator<>(dataGenerator,
                    arrayUpdater, data -> getElement()
                            .callFunction("$connector.updateData", data),
                    getElement().getNode());
        }

        render();

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

        if (loadMode == LoadMode.LAZY && filterChangeRegistration == null) {
            filterChangeRegistration = getElement()
                    .addEventListener("filter-changed", event -> {
                        filterSlot.accept(getFilterString());
                    }).debounce(300);
        }
        settingItems = false;
    }

    /**
     * Sets a list data provider as the data provider of this combo box.
     * Filtering will use a case insensitive match to show all items where the
     * filter text is a substring of the label displayed for that item, which
     * you can configure with
     * {@link #setItemLabelGenerator(ItemLabelGenerator)}.
     * <p>
     * The browser will request for the items lazily as the user scrolls down
     * the combo box. Filtering is done in the server. More responsive
     * client-side filtering can be achieved only by sending all the items to
     * the client at once with the {@link #setItems(Collection)} method.
     *
     * @param listDataProvider
     *            the list data provider to use, not <code>null</code>
     */
    public void setDataProvider(ListDataProvider<T> listDataProvider) {
        // Cannot use the case insensitive contains shorthand from
        // ListDataProvider since it wouldn't react to locale changes
        ItemFilter<T> defaultItemFilter = (item,
                filterText) -> generateLabel(item).toLowerCase(getLocale())
                        .contains(filterText.toLowerCase(getLocale()));

        setDataProvider(defaultItemFilter, listDataProvider);
    }

    /**
     * Sets a CallbackDataProvider using the given fetch items callback and a
     * size callback.
     * <p>
     * This method is a shorthand for making a {@link CallbackDataProvider} that
     * handles a partial {@link com.vaadin.data.provider.Query Query} object.
     *
     * @param fetchItems
     *            a callback for fetching items
     * @param sizeCallback
     *            a callback for getting the count of items
     *
     * @see CallbackDataProvider
     * @see #setDataProvider(DataProvider)
     */
    public void setDataProvider(FetchItemsCallback<T> fetchItems,
            SerializableFunction<String, Integer> sizeCallback) {
        setDataProvider(new CallbackDataProvider<>(
                q -> fetchItems.fetchItems(q.getFilter().orElse(""),
                        q.getOffset(), q.getLimit()),
                q -> sizeCallback.apply(q.getFilter().orElse(""))));
    }

    /**
     * Sets a list data provider with an item filter as the data provider of
     * this combo box. The item filter is used to compare each item to the
     * filter text entered by the user.
     * <p>
     * The browser will request for the items lazily as the user scrolls down
     * the combo box. Filtering is done in the server. More responsive
     * client-side filtering can be achieved only by sending all the items to
     * the client at once with the {@link #setItems(Collection)} method.
     *
     * @param itemFilter
     *            filter to check if an item is shown when user typed some text
     *            into the ComboBox
     * @param listDataProvider
     *            the list data provider to use, not <code>null</code>
     */
    public void setDataProvider(ItemFilter<T> itemFilter,
            ListDataProvider<T> listDataProvider) {
        Objects.requireNonNull(listDataProvider,
                "List data provider cannot be null");

        // Must do getItemLabelGenerator() for each operation since it might
        // not be the same as when this method was invoked
        setDataProvider(listDataProvider,
                filterText -> item -> itemFilter.test(item, filterText));
    }

    /**
     * Gets the data provider used by this ComboBox.
     * 
     * @return the data provider used by this ComboBox
     */
    public DataProvider<T, ?> getDataProvider() {
        return dataCommunicator.getDataProvider();
    }

    /**
     * Gets the list of items which were filtered by the user input.
     *
     * @return the list of filtered items, or empty list if none were filtered
     */
    public List<T> getFilteredItems() {
        if (temporaryFilteredItems != null) {
            return Collections.unmodifiableList(temporaryFilteredItems);
        }
        JsonArray items = super.getFilteredItemsJsonArray();
        List<T> result = new ArrayList<>(items.length());
        for (int i = 0; i < items.length(); i++) {
            JsonObject object = items.get(i);
            String key = object.getString("key");
            result.add(getKeyMapper().get(key));
        }
        return result;
    }

    /**
     * Convenience method for the {@link #setFilteredItems(Collection)}. It sets
     * the list of visible items in reaction of the input of the user.
     *
     * @param filteredItems
     *            the items to show in response of a filter input
     */
    public void setFilteredItems(T... filteredItems) {
        setFilteredItems(Arrays.asList(filteredItems));
    }

    /**
     * It sets the list of visible items in reaction of the input of the user.
     *
     * @param filteredItems
     *            the items to show in response of a filter input
     */
    public void setFilteredItems(Collection<T> filteredItems) {
        temporaryFilteredItems = new ArrayList<>(filteredItems);

        runBeforeClientResponse(ui -> {
            JsonArray jsonArray = generateJson(filteredItems.stream());
            setFilteredItems(jsonArray);
            temporaryFilteredItems = null;
        });
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
        if (dataCommunicator != null) {
            dataCommunicator.reset();
        }
    }

    /**
     * Gets the item label generator that is used to produce the strings shown
     * in the combo box for each item.
     *
     * @return the item label generator used, not null
     */
    public ItemLabelGenerator<T> getItemLabelGenerator() {
        return itemLabelGenerator;
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

    @Override
    public T getEmptyValue() {
        return null;
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
    @Override
    public Registration addCustomValueSetListener(
            ComponentEventListener<CustomValueSetEvent<ComboBox<T>>> listener) {
        setAllowCustomValue(true);
        customValueListenersCount++;
        Registration registration = super.addCustomValueSetListener(listener);
        return new CustomValueRegistration(registration);
    }

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

    private String generateLabel(T item) {
        if (item == null) {
            return nullRepresentation;
        }
        String label = getItemLabelGenerator().apply(item);
        if (label == null) {
            throw new IllegalStateException(String.format(
                    "Got 'null' as a label value for the item '%s'. "
                            + "'%s' instance may not return 'null' values",
                    item, ItemLabelGenerator.class.getSimpleName()));
        }
        return label;
    }

    private JsonArray generateJson(Stream<T> data) {
        return data.map(this::generateData).collect(JsonUtils.asArray());
    }

    private JsonObject generateData(T item) {
        JsonObject json = Json.createObject();
        json.put("key", getKeyMapper().key(item));
        dataGenerator.generateData(item, json);
        return json;
    }

    private void render() {
        if (renderScheduled || dataCommunicator == null || renderer == null) {
            return;
        }
        renderScheduled = true;
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
            renderScheduled = false;
        });
    }

    private void setPageSize(int pageSize) {
        getElement().setProperty("pageSize", pageSize);
    }

    @ClientCallable
    private void confirmUpdate(int id) {
        dataCommunicator.confirmUpdate(id);
    }

    @ClientCallable
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

    private DataKeyMapper<T> getKeyMapper() {
        return dataCommunicator.getKeyMapper();
    }

    private void throwWhenChangingBetweenEagerAndLazy() {
        throw new IllegalStateException(
                "Changing a ComboBox from using a lazy data provider "
                        + "to eager or vice versa is not supported. "
                        + "Use either setItems() or setDataProvider() "
                        + "for one ComboBox.");
    }

}
