package com.vaadin.flow.component.combobox.test;

import com.vaadin.flow.testutil.TestPath;
import org.junit.Test;

@TestPath("comboboxinrendererpage")
public class ComboBoxInRendererIT extends AbstractComboBoxIT {
    @Test
    public void noErrorsInConsole() {
        open();
        checkLogsForErrors();
    }

}
