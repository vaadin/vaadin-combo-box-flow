package com.vaadin.flow.component.combobox.dataview;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

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
        final AtomicInteger itemCount = new AtomicInteger(0);
        dataView.addItemCountChangeListener(
                event -> itemCount.set(event.getItemCount()));
        dataCommunicator.setRequestedRange(0, 50);

        fakeClientCommunication();

        Assert.assertEquals(
                "Expected two item count event from two data communicators",
                3, itemCount.getAndSet(0));

        dataView.setItemCountCallback(query -> 2);

        fakeClientCommunication();

        Assert.assertEquals("Invalid item count reported", 2, itemCount.get());
    }

    @Test
    public void getLazyDataView_emptyDataCommunicator_throws() {
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("Cannot create an instance of "
                + "LazyDataView without setting the backend callback(s). "
                + "Please use one of the ComboBox's 'setItems' methods to "
                + "setup on how the items should be fetched from backend");
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getLazyDataView();
    }

    private void fakeClientCommunication() {
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
        ui.getInternals().getStateTree().collectChanges(ignore -> {
        });
    }

}
