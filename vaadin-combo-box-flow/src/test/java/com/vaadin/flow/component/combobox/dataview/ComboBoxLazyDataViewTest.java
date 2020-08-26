package com.vaadin.flow.component.combobox.dataview;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.data.provider.BackEndDataProvider;
import com.vaadin.flow.data.provider.DataCommunicatorTest;
import com.vaadin.flow.data.provider.DataProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ComboBoxLazyDataViewTest {

    private String [] items = {"foo", "bar", "baz"};
    private ComboBoxLazyDataView<String> dataView;
    private ComboBox<String> comboBox;
    private DataCommunicatorTest.MockUI ui;

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

        dataView = comboBox.setItems(dataProvider);
    }

    @Test
    public void setItemCountCallback_switchFromUndefinedSize_definedSize() {
        Assert.assertTrue(comboBox.getDataCommunicator().isDefinedSize());

        dataView.setItemCountUnknown();
        Assert.assertFalse(comboBox.getDataCommunicator().isDefinedSize());

        dataView.setItemCountCallback(query -> 5);
        Assert.assertTrue(comboBox.getDataCommunicator().isDefinedSize());
    }

    @Test
    public void setItemCountCallback_setAnotherCountCallback_itemCountChanged() {
        final AtomicInteger itemCount = new AtomicInteger(0);
        dataView.addItemCountChangeListener(
                event -> itemCount.set(event.getItemCount()));
        comboBox.getDataCommunicator().setRequestedRange(0, 50);

        dataView.setItemCountCallback(query -> 2);

        fakeClientCommunication();

        Assert.assertEquals("Invalid item count reported", 2, itemCount.get());
    }

    private void fakeClientCommunication() {
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
        ui.getInternals().getStateTree().collectChanges(ignore -> {
        });
    }

}
