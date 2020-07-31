package com.vaadin.flow.component.combobox.dataview;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.data.provider.AbstractListDataViewListenerTest;
import com.vaadin.flow.data.provider.DataKeyMapper;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.HasListDataView;
import com.vaadin.flow.data.provider.ListDataProvider;

public class ComboBoxListDataViewTest extends AbstractListDataViewListenerTest {

    private List<String> items;
    private ComboBoxListDataView<String> dataView;
    private ComboBox<String> component;

    @Before
    public void init() {
        items = new ArrayList<>(Arrays.asList("first", "middle", "last"));
        component = new ComboBox<>();
        dataView = component.setItems(items);
    }

    @Test
    public void getItems_noFiltersSet_allItemsObtained() {
        Stream<String> allItems = dataView.getItems();
        Assert.assertArrayEquals("Unexpected data set", items.toArray(),
                allItems.toArray());
    }

    @Test
    public void getItems_withFilter_filteredItemsObtained() {
        ComboBox<String> comboBox = new ComboBox<String>() {
            @Override
            protected Locale getLocale() {
                return Locale.getDefault();
            }
        };
        comboBox.getElement().setProperty("filter", "middle");
        ComboBoxListDataView<String> dataView = comboBox.setItems(items);
        Stream<String> allItems = dataView.getItems();
        Assert.assertArrayEquals("Unexpected data set",
                new String[] { "middle" }, allItems.toArray());
    }

    @Test
    public void getItemCount_noFilters_totalItemsCountObtained() {
        Assert.assertEquals("Unexpected size for data", items.size(),
                dataView.getItemCount());
    }

    @Test
    public void getItemCount_withFilter_totalItemsCountObtained() {
        dataView.setFilter(item -> item.equalsIgnoreCase("middle"));

        Assert.assertEquals("Unexpected item count", 1,
                dataView.getItemCount());

        Assert.assertTrue("Unexpected item", dataView.contains(items.get(1)));
    }

    @Test
    public void setIdentifierProvider_customIdentifier_keyMapperUsesIdentifier() {
        Item first = new Item(1L, "first");
        Item second = new Item(2L, "middle");

        List<Item> items = new ArrayList<>(Arrays.asList(first, second));

        ListDataProvider<Item> dataProvider = DataProvider.ofCollection(items);
        ComboBox<Item> component = new ComboBox<>();

        ComboBoxListDataView<Item> dataView = component.setItems(dataProvider);
        DataKeyMapper<Item> keyMapper = component.getDataCommunicator()
                .getKeyMapper();
        items.forEach(keyMapper::key);

        Assert.assertFalse(keyMapper.has(new Item(1L, "non-present")));
        dataView.setIdentifierProvider(Item::getId);
        Assert.assertTrue(keyMapper.has(new Item(1L, "non-present")));
    }

    @Override
    protected HasListDataView<String, ComboBoxListDataView<String>> getComponent() {
        return new ComboBox<>();
    }

    private static class Item {
        private long id;
        private String value;

        public Item(long id) {
            this.id = id;
        }

        public Item(long id, String value) {
            this.id = id;
            this.value = value;
        }

        public long getId() {
            return id;
        }

        public String getValue() {
            return value;
        }

        public void setId(long id) {
            this.id = id;
        }

        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Item item = (Item) o;
            return id == item.id && Objects.equals(value, item.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, value);
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }
}