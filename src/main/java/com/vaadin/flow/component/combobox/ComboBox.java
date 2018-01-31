/*
 * Copyright 2000-2017 Vaadin Ltd.
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
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.ItemLabelGenerator;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.data.binder.HasDataProvider;
import com.vaadin.flow.data.provider.ComponentDataGenerator;
import com.vaadin.flow.data.provider.CompositeDataGenerator;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.KeyMapper;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.function.ValueProvider;
import com.vaadin.flow.internal.JsonSerializer;
import com.vaadin.flow.renderer.ComponentTemplateRenderer;
import com.vaadin.flow.renderer.TemplateRenderer;
import com.vaadin.flow.renderer.TemplateRendererUtil;
import com.vaadin.flow.shared.Registration;

import elemental.json.Json;
import elemental.json.JsonArray;
import elemental.json.JsonObject;
import elemental.json.JsonValue;

/**
 * Server-side component for the {@code vaadin-combo-box} webcomponent. It
 * contains the same features of the webcomponent, such as item filtering,
 * object selection and item templating.
 *
 * @param <T>
 *            the type of the items to be inserted in the combo box
 */
public class ComboBox<T> extends GeneratedVaadinComboBox<ComboBox<T>> implements
        HasSize, HasValidation, HasValue<ComboBox<T>, T>, HasDataProvider<T> {
    private static final String ITEM_LABEL_PROPERTY = "label";
    private static final String KEY_PROPERTY = "key";
    private static final String SELECTED_ITEM_PROPERTY_NAME = "selectedItem";
    private static final String TEMPLATE_TAG_NAME = "template";

    private T oldValue;
    private ItemLabelGenerator<T> itemLabelGenerator = String::valueOf;

    private DataProvider<T, ?> dataProvider = DataProvider.ofItems();
    private final CompositeDataGenerator<T> dataGenerator = new CompositeDataGenerator<>();

    private final KeyMapper<T> keyMapper = new KeyMapper<>();
    private final Element template;
    private TemplateRenderer<T> renderer;

    private List<T> itemsFromDataProvider = Collections.emptyList();
    private Registration rendererRegistration;
    private Registration componentRendererRegistration;
    private boolean refreshScheduled;
    private boolean setItemScheduled;

    private T temporarySelectedItem;
    private List<T> temporaryFilteredItems;

    /**
     * Default constructor. Creates an empty combo box.
     *
     */
    public ComboBox() {
        getElement().synchronizeProperty(SELECTED_ITEM_PROPERTY_NAME,
                "selected-item-changed");
        getElement().synchronizeProperty(SELECTED_ITEM_PROPERTY_NAME, "change");

        getElement().addEventListener("selected-item-changed", event -> {
            fireEvent(new HasValue.ValueChangeEvent<>(this, this, oldValue,
                    true));
            oldValue = getValue();
        });

        setItemValuePath(KEY_PROPERTY);

        renderer = TemplateRenderer.of("[[item.label]]");

        template = new Element(TEMPLATE_TAG_NAME);
        getElement().appendChild(template);
        setItemRenderer(renderer);
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
    public void setItemRenderer(TemplateRenderer<T> renderer) {
        Objects.requireNonNull(renderer, "The renderer can not be null");

        unregister(componentRendererRegistration);
        componentRendererRegistration = null;
        if (renderer instanceof ComponentTemplateRenderer) {
            componentRendererRegistration = setupItemComponentRenderer(this,
                    (ComponentTemplateRenderer<? extends Component, T>) renderer);
        }
        this.renderer = renderer;
        template.setProperty("innerHTML", renderer.getTemplate());

        TemplateRendererUtil.registerEventHandlers(renderer, template,
                getElement(), keyMapper::get);

        unregister(rendererRegistration);
        rendererRegistration = dataGenerator
                .addDataGenerator((item, json) -> applyValueProviders(item,
                        json, renderer.getValueProviders()));
        refresh();
    }

    private void unregister(Registration registration) {
        if (registration != null) {
            registration.remove();
        }
    }

    private void applyValueProviders(T item, JsonObject json,
            Map<String, ValueProvider<T, ?>> valueProviders) {
        valueProviders.forEach((property, provider) -> json.put(property,
                JsonSerializer.toJson(provider.apply(item))));
    }

    @Override
    public void setDataProvider(DataProvider<T, ?> dataProvider) {
        Objects.requireNonNull(dataProvider,
                "The data provider can not be null");
        this.dataProvider = dataProvider;
        itemsFromDataProvider = dataProvider.fetch(new Query<>())
                .collect(Collectors.toList());
        setValue(getEmptyValue());
        refresh();
    }

    public DataProvider<T, ?> getDataProvider() {
        return dataProvider;
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
            result.add(getData(items.get(i)));
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
            setFilteredItems(generateJson(temporaryFilteredItems.stream()));
            temporaryFilteredItems = null;
        });
    }

    /**
     * Sets the item label generator that is used to produce the strings shown
     * in the combo box for each item. By default,
     * {@link String#valueOf(Object)} is used.
     * <p>
     * When the {@link #setItemRenderer(TemplateRenderer)} is used, the
     * ItemLabelGenerator is only used to show the selected item label.
     *
     * @param itemLabelGenerator
     *            the item label provider to use, not null
     */
    public void setItemLabelGenerator(
            ItemLabelGenerator<T> itemLabelGenerator) {
        Objects.requireNonNull(itemLabelGenerator,
                "The item label generator can not be null");
        this.itemLabelGenerator = itemLabelGenerator;
        refresh();
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

    /**
     * Enables or disables this combobox.
     * 
     * @param enabled
     *            the boolean value to set
     */
    public void setEnabled(boolean enabled) {
        setDisabled(!enabled);
    }

    /**
     * Determines whether this combobox is enabled
     * <p>
     * This property is not synchronized automatically from the client side, so
     * the returned value may not be the same as in client side.
     * </p>
     * 
     * @return {@code true} if the combobox is enabled, {@code false} otherwise
     */
    public boolean isEnabled() {
        return !isDisabledBoolean();
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
     * @return the {@code preventInvalidInput} proterty of the combobox
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
     * @return the {@code placeholder} proterty of the combobox
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
     * @return the {@code pattern} proterty of the combobox
     */
    public String getPattern() {
        return getPatternString();
    }

    @Override
    public T getEmptyValue() {
        return null;
    }

    @Override
    public void setValue(T value) {
        if (value == null) {
            temporarySelectedItem = null;
            if (getValue() != null) {
                getElement().setPropertyJson(SELECTED_ITEM_PROPERTY_NAME,
                        Json.createNull());
            }
            return;
        }
        if (itemsFromDataProvider.indexOf(value) < 0) {
            throw new IllegalArgumentException(
                    "The provided value is not part of ComboBox: " + value);
        }
        temporarySelectedItem = value;
        if (setItemScheduled) {
            return;
        }
        setItemScheduled = true;

        runBeforeClientResponse(ui -> {
            setItemScheduled = false;
            if (temporarySelectedItem == null) {
                return;
            }
            int updatedIndex = itemsFromDataProvider
                    .indexOf(temporarySelectedItem);
            ui.getPage().executeJavaScript("$0.selectedItem = $0.items[$1];",
                    this.getElement(), updatedIndex);
            temporarySelectedItem = null;
        });
    }

    @Override
    public T getValue() {
        if (temporarySelectedItem != null) {
            return temporarySelectedItem;
        }
        Serializable property = getElement()
                .getPropertyRaw(SELECTED_ITEM_PROPERTY_NAME);
        if (property instanceof JsonObject) {
            JsonObject selected = (JsonObject) property;
            assert selected.hasKey(KEY_PROPERTY);
            return keyMapper.get(selected.getString(KEY_PROPERTY));
        }
        return getEmptyValue();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Registration addValueChangeListener(
            ValueChangeListener<ComboBox<T>, T> listener) {

        return addListener(HasValue.ValueChangeEvent.class,
                (ValueChangeListener) listener);
    }

    private JsonArray generateJson(Stream<T> data) {
        JsonArray array = Json.createArray();
        data.map(this::generateJson)
                .forEachOrdered(json -> array.set(array.length(), json));
        return array;
    }

    private JsonObject generateJson(T item) {
        JsonObject json = Json.createObject();
        json.put(KEY_PROPERTY, keyMapper.key(item));

        String label = getItemLabelGenerator().apply(item);
        if (label == null) {
            throw new IllegalStateException(String.format(
                    "Got 'null' as a label value for the item '%s'. "
                            + "'%s' instance may not return 'null' values",
                    item, ItemLabelGenerator.class.getSimpleName()));
        }
        json.put(ITEM_LABEL_PROPERTY, label);
        dataGenerator.generateData(item, json);
        return json;
    }

    private T getData(JsonObject item) {
        if (item == null) {
            return null;
        }
        assert item.hasKey(KEY_PROPERTY);
        JsonValue key = item.get(KEY_PROPERTY);
        return keyMapper.get(key.asString());
    }

    private void refresh() {
        keyMapper.removeAll();
        if (refreshScheduled) {
            return;
        }
        refreshScheduled = true;
        runBeforeClientResponse(ui -> {
            JsonArray array = generateJson(itemsFromDataProvider.stream());
            setItems(array);
            refreshScheduled = false;
        });

    }

    private void setItemValuePath(String path) {
        getElement().setProperty("itemValuePath", path == null ? "" : path);
    }

    private Registration setupItemComponentRenderer(Component owner,
            ComponentTemplateRenderer<? extends Component, T> componentRenderer) {

        Element container = new Element("div", false);
        owner.getElement().appendVirtualChild(container);

        String appId = UI.getCurrent().getInternals().getAppId();

        componentRenderer.setTemplateAttribute("appid", appId);
        componentRenderer.setTemplateAttribute("nodeid", "[[item.nodeId]]");

        return dataGenerator.addDataGenerator(new ComponentDataGenerator<>(
                componentRenderer, container, "nodeId", keyMapper));
    }

    void runBeforeClientResponse(Consumer<UI> command) {
        getElement().getNode().runWhenAttached(
                ui -> ui.beforeClientResponse(this, () -> command.accept(ui)));
    }
}
