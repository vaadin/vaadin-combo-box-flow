/*
 * Copyright 2000-2020 Vaadin Ltd.
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

package com.vaadin.flow.component.combobox.test.dataview;

import static com.vaadin.flow.component.combobox.test.dataview.AbstractItemCountComboBoxPage.DEFAULT_DATA_PROVIDER_SIZE;

import org.junit.Assert;
import org.junit.Test;

import com.vaadin.flow.internal.Range;
import com.vaadin.flow.testutil.TestPath;

@TestPath("item-count-unknown")
public class ItemCountUnknownComboBoxIT extends AbstractItemCountComboBoxIT {

    @Test
    public void undefinedSize_defaultPageSizeEvenToDatasetSize_scrollingToEnd() {
        final int datasetSize = 500;
        open(datasetSize);

        verifyItemsSize(getDefaultInitialItemCount());
        verifyFetchForUndefinedSizeCallback(0, Range.withLength(0, pageSize));

        doScroll(45, getDefaultInitialItemCount(), 1, 50, 150);

        // trigger next page fetch and size buffer increase
        doScroll(110, 400, 2, 150, 200);

        // jump over a page, trigger fetch
        doScroll(270, 400, 3, 250, 350);

        // trigger another buffer increase but not capping size
        doScroll(395, 600, 4, 350, 450);

        // scroll to actual end, no more items returned and size is adjusted
        doScroll(500, 500, 5, 450, 500);
        Assert.assertEquals(499, getItems(comboBoxElement).size());
        // TODO Is it relevant to ComboBox?
        // TODO #1038 test further after grid is note fetching extra stuff when
        // size has been adjusted to less than what it is
        // doScroll(0, 500, 6, 0, 100);
        // doScroll(450, 500, 7, 400, 500);
    }

    @Test
    public void undefinedSize_switchesToDefinedSize_sizeChanges() {
        int actualSize = 300;
        open(actualSize);

        verifyItemsSize(getDefaultInitialItemCount());
        verifyFetchForUndefinedSizeCallback(0, Range.withLength(0, pageSize));

        doScroll(120, 400, 1, 50, 200);

        doScroll(299, actualSize, 2, 150, actualSize);

        Assert.assertEquals(300, getItems(comboBoxElement).size());

        // change callback backend size limit
        setUnknownCountBackendSize(DEFAULT_DATA_PROVIDER_SIZE);
        // TODO is that relevant to ComboBox?
        // grid has scrolled to end -> switch to defined size callback
        // -> new size updated and more items fetched
        setCountCallback();

        verifyItemsSize(DEFAULT_DATA_PROVIDER_SIZE);
        // new rows are added to end due to size increase
        Assert.assertEquals(301, getItems(comboBoxElement).size());

        scrollToItem(comboBoxElement, 500);

        int expectedLastItem = 517;
        Assert.assertEquals(
                "ComboBox should be able to scroll after changing to defined " +
                        "size",
                expectedLastItem, getItems(comboBoxElement).size());

        // switching back to undefined size, nothing changes
        setUnknownCount();

        verifyItemsSize(DEFAULT_DATA_PROVIDER_SIZE);
        Assert.assertEquals(expectedLastItem, getItems(comboBoxElement).size());

        // increase backend size and scroll to current end
        setUnknownCountBackendSize(2000);
        // size has been increased again by default size
        doScroll(1000, 1200, 6, 950, 1100);

        Assert.assertEquals(1001, getItems(comboBoxElement).size());
    }

    // @Test TODO
    public void undefinedSize_switchesToInitialEstimateSizeLargerThanCurrentEstimate_sizeChanges() {

    }

    // @Test TODO
    public void undefinedSize_switchesToInitialEstimateSizeLessThanCurrentEstimate_estimateDiscarded() {

    }

    // @Test TODO
    public void undefinedSize_switchesToEstimateCallback_sizeChanges() {

    }

    // @Test TODO
    public void undefinedSize_switchesToEstimateCallbackSizeLessThanCurrent_throws() {

    }
}
