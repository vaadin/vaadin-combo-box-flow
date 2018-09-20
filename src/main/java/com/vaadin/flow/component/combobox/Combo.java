package com.vaadin.flow.component.combobox;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.vaadin.flow.component.ClientCallable;
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
import com.vaadin.flow.internal.JsonSerializer;
import com.vaadin.flow.internal.JsonUtils;
import com.vaadin.flow.shared.Registration;

import elemental.json.Json;
import elemental.json.JsonObject;
import elemental.json.JsonValue;

@HtmlImport("frontend://flow-component-renderer.html")
@HtmlImport("frontend://b_components/vaadin-combo-box/src/vaadin-combo-box.html")
@JavaScript("frontend://comboBoxConnector.js")
public class Combo<T> extends GeneratedVaadinComboBox<Combo<T>, T>
        implements HasFilterableDataProvider<T, String> {

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
            enqueue("$connector.set", start,
                    items.stream().collect(JsonUtils.asArray()));
        }

        @Override
        public void clear(int start, int length) {
            enqueue("$connector.clear", start, length);
        }

        @Override
        public void commit(int updateId) {
            // getDataCommunicator().confirmUpdate(updateId);
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

    private ItemLabelGenerator<T> itemLabelGenerator = String::valueOf;

    private Renderer<T> renderer;

    private final CompositeDataGenerator<T> dataGenerator = new CompositeDataGenerator<>();
    private Registration dataGeneratorRegistration;

    private Element template;

    private final DataCommunicator<T> dataCommunicator = new DataCommunicator<>(
            dataGenerator, arrayUpdater,
            data -> getElement().callFunction("$connector.updateData", data),
            getElement().getNode());

    public Combo() {
        super(null, null, JsonValue.class, Combo::presentationToModel,
                Combo::modelToPresentation);
        dataGenerator.addDataGenerator(
                (item, jsonObject) -> renderer.getValueProviders()
                        .forEach((property, provider) -> jsonObject.put(
                                property,
                                JsonSerializer.toJson(provider.apply(item)))));
        template = new Element("template");
        getElement().appendChild(template);
        setRenderer(String::valueOf);

        addFilterChangeListener(event -> {
            // getDataCommunicator().getDataProvider().refreshAll();
            filterSlot.accept(event.getFilter());
        });
    }

    public String getFilterString() {
        return super.getFilterString();
    }

    private static <T> T presentationToModel(Combo<T> comboBox,
            JsonValue presentation) {
        return comboBox.getValue();
    }

    private static <T> JsonValue modelToPresentation(Combo<T> comboBox,
            T model) {
        // return null;
        if (model == null) {
            return Json.createNull();
        }
        int updatedIndex = comboBox.itemsFromDataProvider.indexOf(model);
        if (updatedIndex < 0) {
            throw new IllegalArgumentException(
                    "The provided value is not part of ComboBox: " + model);
        }
        return comboBox
                .generateJson(comboBox.itemsFromDataProvider.get(updatedIndex));
    }

    @Override
    public T getValue() {
        return getValue(
                getElement().getPropertyRaw(SELECTED_ITEM_PROPERTY_NAME));
    }

    private T getValue(Serializable value) {
        if (value instanceof JsonObject) {
            JsonObject selected = (JsonObject) value;
            assert selected.hasKey(KEY_PROPERTY);
            return getKeyMapper().get(selected.getString(KEY_PROPERTY));
        }
        return getEmptyValue();
    }

    public void setRenderer(ValueProvider<T, String> valueProvider) {
        Objects.requireNonNull(valueProvider,
                "The valueProvider must not be null");
        setRenderer(TemplateRenderer.<T> of("[[item.label]]")
                .withProperty("label", valueProvider));
    }

    public void setRenderer(Renderer<T> renderer) {
        Objects.requireNonNull(renderer, "The renderer must not be null");

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

        this.renderer = renderer;

        getDataCommunicator().reset();
    }

    @ClientCallable(DisabledUpdateMode.ALWAYS)
    private void confirmUpdate(int id) {
        getDataCommunicator().confirmUpdate(id);
    }

    @ClientCallable(DisabledUpdateMode.ALWAYS)
    private void setRequestedRange(int start, int length) {
        getDataCommunicator().setRequestedRange(start, length);
    }

    public DataCommunicator<T> getDataCommunicator() {
        return dataCommunicator;
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

    // @Override
    // public void setDataProvider(DataProvider<T, String> dataProvider) {
    // Objects.requireNonNull(dataProvider,
    // "The data provider can not be null");
    //
    // getDataCommunicator().setDataProvider(dataProvider, null);
    // }

    @Override
    public void setItems(Collection<T> items) {
        // TODO Auto-generated method stub

    }

    @Override
    public <C> void setDataProvider(DataProvider<T, C> dataProvider,
            SerializableFunction<String, C> filterConverter) {
        Objects.requireNonNull(dataProvider, "dataProvider cannot be null");
        Objects.requireNonNull(filterConverter,
                "filterConverter cannot be null");

        SerializableFunction<String, C> convertOrNull = filterText -> {
            if (filterText == null || filterText.isEmpty()) {
                return null;
            }

            return filterConverter.apply(filterText);
        };

        SerializableConsumer<C> providerFilterSlot = getDataCommunicator()
                .setDataProvider(dataProvider,
                        convertOrNull.apply(getFilterString()));

        filterSlot = filter -> providerFilterSlot
                .accept(convertOrNull.apply(filter));

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

    public ItemLabelGenerator<T> getItemLabelGenerator() {
        return itemLabelGenerator;
    }

    @Override
    public T getEmptyValue() {
        return null;
    }

    private DataProvider<T, ?> getDataProvider() {
        return getDataCommunicator().getDataProvider();
    }

    private DataKeyMapper<T> getKeyMapper() {
        return getDataCommunicator().getKeyMapper();
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
