package com.vaadin.flow.component.combobox.dataview;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.data.provider.ArrayUpdater;
import com.vaadin.flow.data.provider.BackEndDataProvider;
import com.vaadin.flow.data.provider.DataCommunicator;
import com.vaadin.flow.data.provider.DataCommunicatorTest;
import com.vaadin.flow.data.provider.DataProvider;

import elemental.json.JsonValue;

public class ComboBoxLazyDataViewTest {

    private String[] items = { "foo", "bar", "baz" };
    private ComboBoxLazyDataView<String> dataView;
    private ComboBox<String> comboBox;
    private DataCommunicatorTest.MockUI ui;
    private DataCommunicator<String> dataCommunicator;
    private ArrayUpdater arrayUpdater;

    @Before
    public void setup() {
        BackEndDataProvider<String, String> dataProvider = DataProvider
                .fromFilteringCallbacks(query -> {
                    query.getOffset();
                    query.getLimit();
                    return Stream.of(items);
                }, query -> 3);

        comboBox = new ComboBox<>();
        ui = new DataCommunicatorTest.MockUI();
        ui.add(comboBox);

        ArrayUpdater.Update update = new ArrayUpdater.Update() {

            @Override
            public void clear(int start, int length) {

            }

            @Override
            public void set(int start, List<JsonValue> items) {

            }

            @Override
            public void commit(int updateId) {

            }
        };

        arrayUpdater = Mockito.mock(ArrayUpdater.class);
        Mockito.when(arrayUpdater.startUpdate(Mockito.anyInt()))
                .thenReturn(update);

        dataCommunicator = new DataCommunicator<>((item, jsonObject) -> {
        }, arrayUpdater, null, comboBox.getElement().getNode());

        dataCommunicator.setDataProvider(dataProvider, null);
        dataCommunicator.setPageSize(50);

        dataView = new ComboBoxLazyDataView<>(dataCommunicator, comboBox);
    }

    @Test
    public void setItemCountCallback_switchFromUndefinedSize_definedSize() {
        Assert.assertTrue(dataCommunicator.isDefinedSize());

        dataView.setItemCountUnknown();
        Assert.assertFalse(dataCommunicator.isDefinedSize());

        dataView.setItemCountCallback(query -> 5);
        Assert.assertTrue(dataCommunicator.isDefinedSize());
    }

    @Test
    public void setItemCountCallback_setAnotherCountCallback_itemCountChanged() {
        final List<Integer> itemCounts = new ArrayList<>(2);
        dataView.addItemCountChangeListener(
                event -> itemCounts.add(event.getItemCount()));
        dataCommunicator.setRequestedRange(0, 50);

        fakeClientCommunication();

        // size = 3 from test data provider (this.dataProvider) and size = 0
        // from empty list data provider created by default in combo box
        // constructor
        Assert.assertArrayEquals(
                "Expected two item count event from two data communicators",
                new Integer[] { 3, 0 }, itemCounts.toArray());

        itemCounts.clear();

        dataView.setItemCountCallback(query -> 2);

        fakeClientCommunication();

        Assert.assertArrayEquals("Invalid item count reported",
                new Integer[] { 2 }, itemCounts.toArray());
    }

    private void fakeClientCommunication() {
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
        ui.getInternals().getStateTree().collectChanges(ignore -> {
        });
    }

}
