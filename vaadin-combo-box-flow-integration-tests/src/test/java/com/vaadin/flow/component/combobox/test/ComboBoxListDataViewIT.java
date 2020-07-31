package com.vaadin.flow.component.combobox.test;

import static com.vaadin.flow.component.combobox.test.ComboBoxListDataViewPage.FIRST_NAME_FILTER;
import static com.vaadin.flow.component.combobox.test.ComboBoxListDataViewPage.ITEM_COUNT;
import static com.vaadin.flow.component.combobox.test.ComboBoxListDataViewPage.ITEM_DATA;
import static com.vaadin.flow.component.combobox.test.ComboBoxListDataViewPage.ITEM_SELECT;
import static com.vaadin.flow.component.combobox.test.ComboBoxListDataViewPage.REMOVE_ITEM;
import static com.vaadin.flow.component.combobox.test.ComboBoxListDataViewPage.REVERSE_SORTING;
import static com.vaadin.flow.component.combobox.test.ComboBoxListDataViewPage.SHOW_ITEM_DATA;
import static com.vaadin.flow.component.combobox.test.ComboBoxListDataViewPage.SHOW_NEXT_DATA;
import static com.vaadin.flow.component.combobox.test.ComboBoxListDataViewPage.SHOW_PREVIOUS_DATA;

import org.junit.Assert;
import org.junit.Test;

import com.vaadin.flow.component.combobox.testbench.ComboBoxElement;
import com.vaadin.flow.component.textfield.testbench.IntegerFieldElement;
import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
import com.vaadin.flow.testutil.TestPath;
import org.openqa.selenium.Keys;

@TestPath("combobox-list-data-view-page")
public class ComboBoxListDataViewIT extends AbstractComboBoxIT {

    @Test
    public void comboBoxDataViewReturnsExpectedData() {
        open();
        ComboBoxElement comboBox = $(ComboBoxElement.class).first();

        Assert.assertEquals("Item count not expected", "250",
                $("span").id(ITEM_COUNT).getText());

        Assert.assertEquals("Initial selection should be 0", "0",
                $(IntegerFieldElement.class).id(ITEM_SELECT).getValue());

        Assert.assertFalse("Item row 0 should not have previous data.",
                isButtonEnabled(SHOW_PREVIOUS_DATA));
        Assert.assertTrue("Item row 0 has next data.",
                isButtonEnabled(SHOW_NEXT_DATA));

        clickButton(SHOW_ITEM_DATA);

        Assert.assertEquals("Item: Person 1",
                $("span").id(ITEM_DATA).getText());

        clickButton(SHOW_NEXT_DATA);
        Assert.assertEquals("Item: Person 2",
                $("span").id(ITEM_DATA).getText());

        $(IntegerFieldElement.class).id(ITEM_SELECT).setValue("5");

        clickButton(SHOW_ITEM_DATA);
        Assert.assertEquals("Wrong row item", "Item: Person 6",
                $("span").id(ITEM_DATA).getText());

        clickButton(SHOW_NEXT_DATA);
        Assert.assertEquals("Wrong next item.", "Item: Person 7",
                $("span").id(ITEM_DATA).getText());

        clickButton(SHOW_PREVIOUS_DATA);
        Assert.assertEquals("Wrong previous item.", "Item: Person 5",
                $("span").id(ITEM_DATA).getText());

        $(TextFieldElement.class).id(FIRST_NAME_FILTER).setValue("9");

        // There are 43 firstnames with a 9 in the set from 1-250
        Assert.assertEquals("Filtered size not as expected", "43",
                $("span").id(ITEM_COUNT).getText());

        comboBox.openPopup();
        assertLoadedItemsCount("Should be 43 items after filtering", 43,
                comboBox);

        // Add custom value
        $(TextFieldElement.class).id(FIRST_NAME_FILTER).setValue("");
        comboBox.sendKeys("Person NEW", Keys.ENTER);
        Assert.assertEquals("Expected item count = 251 after adding a new item",
                "251", $("span").id(ITEM_COUNT).getText());
        comboBox.openPopup();
        Assert.assertEquals("Expected item count = 251 after adding a new " +
                        "item and pop up",
                "251", $("span").id(ITEM_COUNT).getText());

        // Remove recently added item
        $(IntegerFieldElement.class).id(ITEM_SELECT).setValue("250");
        clickButton(REMOVE_ITEM);
        comboBox.openPopup();
        Assert.assertEquals("Expected item count = 250 after removing an item",
                "250", $("span").id(ITEM_COUNT).getText());
        Assert.assertEquals("Item count not expected", "250",
                $("span").id(ITEM_COUNT).getText());

        // Sorting
        clickButton(REVERSE_SORTING);

        $(IntegerFieldElement.class).id(ITEM_SELECT).setValue("0");
        clickButton(SHOW_ITEM_DATA);

        // Person 99 is the biggest string in terms of native string comparison
        Assert.assertEquals("Item: Person 99",
                $("span").id(ITEM_DATA).getText());
    }
}
