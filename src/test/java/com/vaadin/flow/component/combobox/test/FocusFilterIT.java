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
package com.vaadin.flow.component.combobox.test;

import com.vaadin.flow.component.combobox.ComboBoxElementUpdated;
import com.vaadin.flow.component.combobox.testbench.ComboBoxElement;
import com.vaadin.flow.testutil.AbstractComponentIT;
import com.vaadin.flow.testutil.TestPath;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@TestPath("focus-filter")
public class FocusFilterIT extends AbstractComboBoxIT {
    @Before
    public void init() {
        open();
        waitUntil(driver -> findElements(By.tagName("vaadin-combo-box"))
                .size() > 0);
    }

    @Test
    public void filter_itemsShouldBeThere() {
        open();
        ComboBoxElement comboBox = $(ComboBoxElementUpdated.class).first();

        comboBox.sendKeys("2");

        waitForItems(comboBox, items -> items.size() == 1 && "Option 2".equals(getItem(items, 0)));

        comboBox.sendKeys(Keys.BACK_SPACE);

        waitForItems(comboBox, items -> items.size() == 4);

        comboBox.sendKeys("3");

        waitForItems(comboBox, items -> items.size() == 1 && "Option 3".equals(getItem(items, 0)));
    }

}
