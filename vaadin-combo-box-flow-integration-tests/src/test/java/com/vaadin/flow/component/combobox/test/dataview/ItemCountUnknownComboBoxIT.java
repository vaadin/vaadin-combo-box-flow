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

        doScroll(45, getDefaultInitialItemCount(), 1, 50, 100,
                "Callback Item 45");

        // trigger next page fetch and size buffer increase
        doScroll(120, 400, 2, 100, 200, "Callback Item 120");

        // jump over a page, trigger fetch
        doScroll(270, 400, 3, 250, 250, "Callback Item 270");

        // trigger another buffer increase but not capping size
        doScroll(395, 600, 4, 250, 350, "Callback Item 395");

        // scroll to actual end, no more items returned and size is adjusted
        doScroll(499, 500, 5, 450, 500, "Callback Item 499");

        // scroll to 0 position and check the size is correct
        doScroll(0, 500, 6, 0, 100, "Callback Item 0");

        // scroll again to the end of list and check the size
        doScroll(450, 500, 7, 400, 500, "Callback Item 450");
    }

    @Test
    public void undefinedSize_switchesToDefinedSize_sizeChanges() {
        int actualSize = 300;
        open(actualSize);

        verifyItemsSize(getDefaultInitialItemCount());
        verifyFetchForUndefinedSizeCallback(0, Range.withLength(0, pageSize));

        doScroll(120, 400, 1, 50, 200, "Callback Item 120");

        doScroll(299, actualSize, 2, 150, actualSize, "Callback Item 299");

        Assert.assertEquals(300, getItems(comboBoxElement).size());

        // change callback backend size limit
        setUnknownCountBackendSize(DEFAULT_DATA_PROVIDER_SIZE);
        // combo box has scrolled to end -> switch to defined size callback
        // -> new size updated and more items fetched
        setCountCallback();

        // Open dropdown and scroll to the end again, check the DP size
        doScroll(299, DEFAULT_DATA_PROVIDER_SIZE, 2, 150, actualSize,
                "Callback Item 299");

        // Check that combo box is scrolled over 'actualSize' after switching
        // to defined size
        doScroll(500, DEFAULT_DATA_PROVIDER_SIZE, 2, 150, actualSize,
                "Callback Item 500");

        // switching back to undefined size, nothing changes
        setUnknownCount();

        doScroll(500, DEFAULT_DATA_PROVIDER_SIZE, 2, 150, actualSize,
                "Callback Item 500");

        // increase backend size and scroll to current end
        setUnknownCountBackendSize(2000);
        // size has been increased again by default size
        doScroll(1000, 1200, 6, 950, 1100, "Callback Item 1000");
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
