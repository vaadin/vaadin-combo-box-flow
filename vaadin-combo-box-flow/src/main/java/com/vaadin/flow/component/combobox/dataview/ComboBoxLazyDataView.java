package com.vaadin.flow.component.combobox.dataview;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.data.provider.AbstractLazyDataView;
import com.vaadin.flow.data.provider.DataCommunicator;

/**
 * Data view implementation for ComboBox with lazy data fetching. Provides
 * information on the data and allows operations on it.
 *
 * @param <T>
 *            the type of the items in ComboBox
 */
public class ComboBoxLazyDataView<T> extends AbstractLazyDataView<T> {

    /**
     * Creates a new lazy data view for ComboBox and verifies the passed data
     * provider is compatible with this data view implementation.
     *
     * @param dataCommunicator
     *            the data communicator of the component
     * @param component
     *            the ComboBox
     */
    public ComboBoxLazyDataView(DataCommunicator<T> dataCommunicator,
                                Component component) {
        super(dataCommunicator, component);
    }
}
