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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;

import com.vaadin.flow.component.combobox.testbench.ComboBoxElement;
import com.vaadin.flow.testutil.AbstractComponentIT;
import com.vaadin.flow.testutil.TestPath;
import com.vaadin.testbench.TestBenchElement;

import elemental.json.JsonObject;

@TestPath("lazy-loading")
public class LazyLoadingIT extends AbstractComponentIT {

    private ComboBoxElement stringBox;

    @Before
    public void init() {
        open();
        waitUntil(driver -> findElements(By.tagName("vaadin-combo-box"))
                .size() > 0);
        stringBox = $(ComboBoxElement.class).id("lazy-strings");
    }

    @Test
    public void initiallyEmpty_openPopup_firstPageLoaded() {
        Assert.assertEquals(
                "Lazy loading ComboBox should not have items "
                        + "before opening the dropdown.",
                0, getLoadedItems(stringBox).size());
    }

    @Test
    public void openPopup_firstPageLoaded() {
        stringBox.openPopup();
        Assert.assertEquals(
                "After opening the ComboBox, the first 50 items should be loaded",
                50, getLoadedItems(stringBox).size());
        assertRendered("Item 10");
    }

    @Test
    public void scrollOverlay_morePagesLoaded() {
        stringBox.openPopup();
        scrollToItem(stringBox, 50);

        Assert.assertEquals("There should be 100 items after loading two pages",
                100, getLoadedItems(stringBox).size());
        assertRendered("Item 52");

        scrollToItem(stringBox, 110);

        Assert.assertEquals(
                "There should be 150 items after loading three pages", 150,
                getLoadedItems(stringBox).size());
        assertRendered("Item 115");
    }

    @Test
    public void clickItem_valueChanged() {
        stringBox.openPopup();
        getItemElements().get(2).click();
        assertMessage("Item 2");
    }

    private void assertMessage(String expectedMessage) {
        Assert.assertEquals(expectedMessage, $("div").id("message").getText());
    }

    // Gets all the loaded json items, but they are not necessarily rendered
    private List<JsonObject> getLoadedItems(ComboBoxElement comboBox) {
        List<JsonObject> list = (List<JsonObject>) executeScript(
                "return arguments[0].filteredItems.filter("
                        + "item => !item.vaadinComboBoxPlaceholder);",
                comboBox);
        return list;
    }

    private void assertRendered(String innerHTML) {
        List<String> overlayContents = getOverlayContents();
        Optional<String> matchingItem = overlayContents.stream()
                .filter(s -> s.equals(innerHTML)).findFirst();
        Assert.assertTrue(
                "Expected to find an item with rendered innerHTML: " + innerHTML
                        + "\nRendered items: "
                        + overlayContents.stream().reduce("",
                                (result, next) -> String.format("%s\n- %s",
                                        result, next)),
                matchingItem.isPresent());
    }

    // Gets the innerHTML of all the actually rendered item elements.
    // There's more items loaded though.
    private List<String> getOverlayContents() {
        return getItemElements().stream()
                .map(element -> element.$("div").id("content"))
                .map(element -> element.getPropertyString("innerHTML"))
                .collect(Collectors.toList());
    }

    private List<TestBenchElement> getItemElements() {
        return getOverlay().$("div").id("content").$("vaadin-combo-box-item")
                .all();
    }

    private void scrollToItem(ComboBoxElement comboBox, int index) {
        executeScript("arguments[0].$.overlay._scrollIntoView(arguments[1])",
                comboBox, index);
    }

    private TestBenchElement getOverlay() {
        return $("vaadin-combo-box-overlay").first();
    }

}
