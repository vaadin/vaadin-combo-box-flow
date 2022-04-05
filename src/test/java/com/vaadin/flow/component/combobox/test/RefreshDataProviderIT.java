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
package com.vaadin.flow.component.combobox.test;

import java.util.List;
import java.util.stream.Collectors;

import com.vaadin.testbench.TestBenchElement;
import org.junit.Assert;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

import com.vaadin.flow.testutil.AbstractComponentIT;
import com.vaadin.flow.testutil.TestPath;

@TestPath("refresh-data-provider")
public class RefreshDataProviderIT extends AbstractComponentIT {

    @Test
    public void refreshDataProvider() throws InterruptedException {
        open();

        List<String> items = getItems();
        Assert.assertEquals(
                "Unexpected items in the combobox, there shouldn't be any item",
                0, items.size());

        findElement(By.id("update")).click();

        items = getItems();
        Assert.assertEquals(
                "Unexpected items size. The rendered items size must be 2", 2,
                items.size());
        Assert.assertEquals("Unexpected rendered the first item text", "foo",
                items.get(0));
        Assert.assertEquals("Unexpected rendered the second item text", "bar",
                items.get(1));

    }

    private List<String> getItems() {
        findElement(By.tagName("vaadin-combo-box")).sendKeys(Keys.ARROW_DOWN);
        TestBenchElement overlay = $("vaadin-combo-box-overlay").first();
        TestBenchElement content = overlay.$("*").id("content");
        TestBenchElement selector = content.$("*").id("selector");
        List<String> items = selector.$("vaadin-combo-box-item").all().stream()
                .map(item -> item.$("flow-component-renderer").first())
                .map(WebElement::getText).collect(Collectors.toList());
        return items;
    }

}
